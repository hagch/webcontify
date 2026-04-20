package io.webcontify.app.service;

import io.webcontify.app.model.*;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantSlugResolver;
import io.webcontify.shared.util.SlugGenerator;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppImportService {

    private static final Logger log = LoggerFactory.getLogger(AppImportService.class);

    private final AppService appService;
    private final AppVersionService appVersionService;
    private final AppDependencyService appDependencyService;
    private final AppSnapshotService appSnapshotService;
    private final AppRepository appRepository;
    private final SlugGenerator slugGenerator;
    private final TenantSlugResolver tenantSlugResolver;

    public AppImportService(
            AppService appService,
            AppVersionService appVersionService,
            AppDependencyService appDependencyService,
            AppSnapshotService appSnapshotService,
            AppRepository appRepository,
            SlugGenerator slugGenerator,
            TenantSlugResolver tenantSlugResolver) {
        this.appService = appService;
        this.appVersionService = appVersionService;
        this.appDependencyService = appDependencyService;
        this.appSnapshotService = appSnapshotService;
        this.appRepository = appRepository;
        this.slugGenerator = slugGenerator;
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Transactional(readOnly = true)
    public ImportPreview analyzeBlueprint(AppBlueprint blueprint) {
        var tenantInfo = TenantContext.getTenantInformation();
        UUID tenantId = tenantInfo.tenantId();

        List<ImportPreview.ImportPreviewApp> previewApps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (AppBlueprint.BlueprintApp blueprintApp : blueprint.apps()) {
            boolean slugExists = appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(
                    blueprintApp.slug(), tenantId);

            if (slugExists) {
                String suggestedSlug = generateUniqueSlug(blueprintApp.slug(), tenantId);
                previewApps.add(new ImportPreview.ImportPreviewApp(
                        blueprintApp.slug(),
                        blueprintApp.name(),
                        true,
                        suggestedSlug));
                warnings.add("App slug '" + blueprintApp.slug()
                        + "' already exists, suggested: '" + suggestedSlug + "'");
            } else {
                previewApps.add(new ImportPreview.ImportPreviewApp(
                        blueprintApp.slug(),
                        blueprintApp.name(),
                        false,
                        blueprintApp.slug()));
            }
        }

        return new ImportPreview(true, previewApps, warnings);
    }

    public List<App> executeImport(AppBlueprint blueprint, Map<String, String> slugOverrides) {
        List<AppBlueprint.BlueprintApp> sorted = topologicalSort(blueprint.apps());

        Map<String, UUID> slugToCreatedId = new HashMap<>();
        List<App> createdApps = new ArrayList<>();

        for (AppBlueprint.BlueprintApp blueprintApp : sorted) {
            String finalSlug = slugOverrides.getOrDefault(blueprintApp.slug(), blueprintApp.slug());
            String finalName = finalSlug.equals(blueprintApp.slug())
                    ? blueprintApp.name()
                    : blueprintApp.name() + " (imported)";

            App newApp = new App();
            newApp.setName(finalName);
            newApp.setDescription(blueprintApp.description());

            App created = appService.create(newApp);
            slugToCreatedId.put(blueprintApp.slug(), created.getId());
            createdApps.add(created);

            appVersionService.publish(created.getId(), blueprintApp.version(), "Imported version");
        }

        for (AppBlueprint.BlueprintApp blueprintApp : sorted) {
            UUID appId = slugToCreatedId.get(blueprintApp.slug());
            for (String depSlug : blueprintApp.dependencies()) {
                UUID depId = slugToCreatedId.get(depSlug);
                if (depId != null) {
                    appDependencyService.addDependency(appId, depId);
                } else {
                    log.warn("Dependency slug '{}' not found in import, skipping", depSlug);
                }
            }
        }

        return createdApps;
    }

    List<AppBlueprint.BlueprintApp> topologicalSort(List<AppBlueprint.BlueprintApp> apps) {
        Map<String, AppBlueprint.BlueprintApp> bySlug = apps.stream()
                .collect(Collectors.toMap(AppBlueprint.BlueprintApp::slug, Function.identity()));

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        apps.forEach(a -> inDegree.put(a.slug(), 0));

        apps.forEach(a -> a.dependencies().forEach(dep -> {
            if (inDegree.containsKey(dep)) {
                inDegree.merge(dep, 1, Integer::sum);
            }
        }));

        // Note: inDegree counts how many apps DEPEND ON this app.
        // Apps with high inDegree should come first (they are depended upon).
        // Actually, we need the reverse: apps with no incoming dependencies come first.
        // An app's "incoming dependency" = it appears in another app's dependency list.
        // So: inDegree here counts how many OTHER apps list this app as dependency.
        // Apps with 0 inDegree have nobody depending on them -> they should come LAST.
        // Apps depended upon by many should come FIRST.

        // Correction: use standard Kahn's with correct direction.
        // Edge: A depends on B means B must come before A.
        // inDegree of A = number of dependencies A has.
        Map<String, Integer> correctInDegree = new LinkedHashMap<>();
        apps.forEach(a -> correctInDegree.put(a.slug(), 0));
        apps.forEach(a -> {
            int count = (int) a.dependencies().stream()
                    .filter(correctInDegree::containsKey)
                    .count();
            correctInDegree.put(a.slug(), count);
        });

        Queue<String> queue = new LinkedList<>();
        correctInDegree.forEach((slug, degree) -> {
            if (degree == 0) {
                queue.add(slug);
            }
        });

        List<AppBlueprint.BlueprintApp> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(bySlug.get(current));

            // Reduce inDegree of apps that depend on current
            apps.forEach(a -> {
                if (a.dependencies().contains(current) && correctInDegree.containsKey(a.slug())) {
                    int newDegree = correctInDegree.get(a.slug()) - 1;
                    correctInDegree.put(a.slug(), newDegree);
                    if (newDegree == 0) {
                        queue.add(a.slug());
                    }
                }
            });
        }

        // If not all apps are in result (cycle), add remaining
        if (result.size() < apps.size()) {
            Set<String> added = result.stream()
                    .map(AppBlueprint.BlueprintApp::slug)
                    .collect(Collectors.toSet());
            apps.stream()
                    .filter(a -> !added.contains(a.slug()))
                    .forEach(result::add);
        }

        return result;
    }

    private String generateUniqueSlug(String baseSlug, UUID tenantId) {
        String candidate = baseSlug + "-imported";
        if (!appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(candidate, tenantId)) {
            return candidate;
        }
        int suffix = 2;
        while (appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(
                candidate + "-" + suffix, tenantId)) {
            suffix++;
        }
        return candidate + "-" + suffix;
    }
}
