package io.webcontify.app.service;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppDependency;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppDependencyEntity;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.*;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppDependencyService {

    private final AppDependencyRepository dependencyRepository;
    private final AppRepository appRepository;
    private final AppService appService;
    private final ChangeRecordService changeRecordService;

    public AppDependencyService(
            AppDependencyRepository dependencyRepository,
            AppRepository appRepository,
            AppService appService,
            ChangeRecordService changeRecordService) {
        this.dependencyRepository = dependencyRepository;
        this.appRepository = appRepository;
        this.appService = appService;
        this.changeRecordService = changeRecordService;
    }

    public AppDependency addDependency(UUID appId, UUID dependsOnAppId) {
        if (appId.equals(dependsOnAppId)) {
            throw new BadRequestException("app.dependency.self_reference");
        }

        App app = appService.getById(appId);
        App dependsOnApp = appService.getById(dependsOnAppId);

        if (dependsOnApp.getStatus() != AppStatus.ACTIVE) {
            throw new BadRequestException("app.dependency.target_not_active");
        }

        if (dependencyRepository.existsByAppIdAndDependsOnId(appId, dependsOnAppId)) {
            throw new ConflictException("app.dependency.already_exists");
        }

        if (wouldCreateCycle(appId, dependsOnAppId)) {
            throw new ConflictException("app.dependency.circular");
        }

        var tenantInfo = TenantContext.getTenantInformation();

        AppDependencyEntity entity = new AppDependencyEntity();
        entity.setAppId(appId);
        entity.setDependsOnId(dependsOnAppId);
        entity.setTenantId(tenantInfo.tenantId());
        dependencyRepository.save(entity);

        markAsDraft(appId);
        String payload = "[{\"op\":\"add\",\"path\":\"/dependencies/" + dependsOnApp.getSlug()
                + "\",\"value\":\"" + dependsOnApp.getName() + "\"}]";
        changeRecordService.create("app", appId, "updated", payload,
                "user:" + tenantInfo.tenantId(), "draft");

        AppDependency result = new AppDependency();
        result.setAppId(appId);
        result.setDependsOnId(dependsOnAppId);
        result.setTenantId(tenantInfo.tenantId());
        result.setDependsOnAppName(dependsOnApp.getName());
        result.setDependsOnAppSlug(dependsOnApp.getSlug());
        result.setDirect(true);
        return result;
    }

    public void removeDependency(UUID appId, UUID dependsOnAppId) {
        if (!dependencyRepository.existsByAppIdAndDependsOnId(appId, dependsOnAppId)) {
            throw new NotFoundException("app.dependency.not_found");
        }

        App dependsOnApp = appService.getById(dependsOnAppId);
        dependencyRepository.deleteByAppIdAndDependsOnId(appId, dependsOnAppId);

        markAsDraft(appId);
        var tenantInfo = TenantContext.getTenantInformation();
        String payload = "[{\"op\":\"remove\",\"path\":\"/dependencies/" + dependsOnApp.getSlug()
                + "\",\"value\":\"" + dependsOnApp.getName() + "\"}]";
        changeRecordService.create("app", appId, "updated", payload,
                "user:" + tenantInfo.tenantId(), "draft");
    }

    @Transactional(readOnly = true)
    public List<AppDependency> listDependencies(UUID appId) {
        appService.getById(appId);

        List<AppDependencyEntity> directEntities = dependencyRepository.findByAppId(appId);
        Set<UUID> directIds = new HashSet<>();
        for (AppDependencyEntity entity : directEntities) {
            directIds.add(entity.getDependsOnId());
        }

        Set<UUID> allDependencyIds = new LinkedHashSet<>(directIds);
        Queue<UUID> queue = new LinkedList<>(directIds);
        Set<UUID> visited = new HashSet<>(directIds);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            List<AppDependencyEntity> transitiveDeps = dependencyRepository.findByAppId(current);
            for (AppDependencyEntity dep : transitiveDeps) {
                UUID depId = dep.getDependsOnId();
                if (visited.add(depId)) {
                    allDependencyIds.add(depId);
                    queue.add(depId);
                }
            }
        }

        List<AppDependency> result = new ArrayList<>();
        for (UUID depId : allDependencyIds) {
            App depApp = appService.getById(depId);
            AppDependency dependency = new AppDependency();
            dependency.setAppId(appId);
            dependency.setDependsOnId(depId);
            dependency.setDependsOnAppName(depApp.getName());
            dependency.setDependsOnAppSlug(depApp.getSlug());
            dependency.setDirect(directIds.contains(depId));
            result.add(dependency);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public DependencyGraphResponse getDependencyGraph(UUID appId) {
        App rootApp = appService.getById(appId);

        Map<UUID, App> appMap = new HashMap<>();
        appMap.put(appId, rootApp);

        List<DependencyGraphEdge> edges = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(appId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }

            List<AppDependencyEntity> deps = dependencyRepository.findByAppId(current);
            Set<UUID> directDepsOfRoot = new HashSet<>();
            if (current.equals(appId)) {
                for (AppDependencyEntity dep : deps) {
                    directDepsOfRoot.add(dep.getDependsOnId());
                }
            }

            for (AppDependencyEntity dep : deps) {
                UUID targetId = dep.getDependsOnId();
                if (!appMap.containsKey(targetId)) {
                    appMap.put(targetId, appService.getById(targetId));
                }

                boolean isDirect = current.equals(appId) && directDepsOfRoot.contains(targetId);
                DependencyGraphEdge edge = new DependencyGraphEdge();
                edge.setSource(current);
                edge.setTarget(targetId);
                edge.setDirect(isDirect);
                edges.add(edge);

                queue.add(targetId);
            }
        }

        List<DependencyGraphNode> nodes = new ArrayList<>();
        for (Map.Entry<UUID, App> entry : appMap.entrySet()) {
            App app = entry.getValue();
            DependencyGraphNode node = new DependencyGraphNode();
            node.setId(app.getId());
            node.setName(app.getName());
            node.setSlug(app.getSlug());
            node.setStatus(io.webcontify.generated.model.AppStatus.fromValue(app.getStatus().name()));
            node.setDefinitionCount(0);
            node.setIsCurrent(app.getId().equals(appId));
            nodes.add(node);
        }

        DependencyGraphResponse response = new DependencyGraphResponse();
        response.setNodes(nodes);
        response.setEdges(edges);
        return response;
    }

    @Transactional(readOnly = true)
    public DependencyImpactResponse getImpact(UUID appId, UUID dependsOnAppId) {
        DependencyImpactResponse response = new DependencyImpactResponse();
        response.setAffectedReferences(Collections.emptyList());
        response.setCanRemove(true);
        return response;
    }

    private void markAsDraft(UUID appId) {
        AppEntity appEntity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        if ("ACTIVE".equals(appEntity.getStatus())) {
            appEntity.setHasDraft(true);
            appRepository.save(appEntity);
        }
    }

    private boolean wouldCreateCycle(UUID fromAppId, UUID toAppId) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(toAppId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(fromAppId)) {
                return true;
            }
            if (visited.add(current)) {
                dependencyRepository.findByAppId(current)
                        .forEach(dep -> queue.add(dep.getDependsOnId()));
            }
        }
        return false;
    }
}
