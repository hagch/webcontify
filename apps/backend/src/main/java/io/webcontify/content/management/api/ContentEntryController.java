package io.webcontify.content.management.api;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.ContentQuery;
import io.webcontify.content.management.query.CursorPage;
import io.webcontify.content.management.query.CursorPageRequest;
import io.webcontify.content.management.query.SortCriterion;
import io.webcontify.content.management.service.ContentEntryService;
import io.webcontify.generated.api.ContentEntryApi;
import io.webcontify.generated.model.*;
import io.webcontify.shared.UserDisplayNameProvider;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantContext;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentEntryController implements ContentEntryApi {

    private final ContentEntryService entryService;
    private final ContentEntryMapper entryMapper;
    private final AppRepository appRepository;
    private final UserDisplayNameProvider userDisplayNameProvider;

    public ContentEntryController(ContentEntryService entryService,
                                  ContentEntryMapper entryMapper,
                                  AppRepository appRepository,
                                  UserDisplayNameProvider userDisplayNameProvider) {
        this.entryService = entryService;
        this.entryMapper = entryMapper;
        this.appRepository = appRepository;
        this.userDisplayNameProvider = userDisplayNameProvider;
    }

    @Override
    public ResponseEntity<ContentEntryResponse> createContentEntry(
            String appSlug, String defSlug,
            CreateContentEntryRequest createContentEntryRequest) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID userId = TenantContext.getCurrentUserId();
        UUID appId = resolveAppId(appSlug);

        ContentEntry created = entryService.create(
                tenant.tenantId(), tenant.slug(), appId, defSlug,
                createContentEntryRequest.getValues(), userId);

        Map<UUID, String> displayNames = resolveDisplayNames(created);
        ContentEntryResponse response = entryMapper.toResponse(created, displayNames);
        return ResponseEntity
                .created(URI.create("/api/v1/content/" + appSlug + "/definitions/" + defSlug + "/entries/" + created.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<ContentEntryResponse> getContentEntry(
            String appSlug, String defSlug, UUID entryId) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID appId = resolveAppId(appSlug);

        ContentEntry entry = entryService.findById(
                tenant.tenantId(), tenant.slug(), appId, defSlug, entryId);
        Map<UUID, String> displayNames = resolveDisplayNames(entry);
        return ResponseEntity.ok(entryMapper.toResponse(entry, displayNames));
    }

    @Override
    public ResponseEntity<ContentEntryResponse> updateContentEntry(
            String appSlug, String defSlug, UUID entryId,
            UpdateContentEntryRequest updateContentEntryRequest) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID userId = TenantContext.getCurrentUserId();
        UUID appId = resolveAppId(appSlug);

        ContentEntry updated = entryService.update(
                tenant.tenantId(), tenant.slug(), appId, defSlug, entryId,
                updateContentEntryRequest.getVersion(),
                updateContentEntryRequest.getValues(), userId);

        Map<UUID, String> displayNames = resolveDisplayNames(updated);
        return ResponseEntity.ok(entryMapper.toResponse(updated, displayNames));
    }

    @Override
    public ResponseEntity<Void> deleteContentEntry(
            String appSlug, String defSlug, UUID entryId) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID userId = TenantContext.getCurrentUserId();
        UUID appId = resolveAppId(appSlug);

        entryService.delete(tenant.tenantId(), tenant.slug(), appId, defSlug, entryId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ContentEntryListResponse> listContentEntries(
            String appSlug, String defSlug,
            @Nullable String cursor, Integer size, @Nullable String sort) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID appId = resolveAppId(appSlug);

        CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
        List<SortCriterion> sorts = parseSortParam(sort);
        ContentQuery query = new ContentQuery(List.of(), sorts, pageRequest);

        CursorPage<ContentEntry> page = entryService.query(
                tenant.tenantId(), tenant.slug(), appId, defSlug, query);

        Map<UUID, String> displayNames = resolveDisplayNamesForEntries(page.items());
        return ResponseEntity.ok(entryMapper.toListResponse(page, displayNames));
    }

    @Override
    public ResponseEntity<EntryReferenceListResponse> searchContentEntries(
            String appSlug, String defSlug,
            @Nullable String q, Integer size, @Nullable String cursor) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID appId = resolveAppId(appSlug);

        CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
        CursorPage<EntryReference> page = entryService.search(
                tenant.tenantId(), tenant.slug(), appId, defSlug, q, pageRequest);

        return ResponseEntity.ok(entryMapper.toReferenceListResponse(page));
    }

    @Override
    public ResponseEntity<EntryHistoryListResponse> getContentEntryHistory(
            String appSlug, String defSlug, UUID entryId,
            Integer size, @Nullable String cursor) {
        TenantInformation tenant = TenantContext.getTenantInformation();
        UUID appId = resolveAppId(appSlug);

        CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
        CursorPage<EntryHistoryRecord> page = entryService.getHistory(
                tenant.tenantId(), tenant.slug(), appId, defSlug, entryId, pageRequest);

        Set<UUID> userIds = page.items().stream()
                .map(EntryHistoryRecord::changedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> displayNames = userDisplayNameProvider.resolveDisplayNames(userIds);
        return ResponseEntity.ok(entryMapper.toHistoryListResponse(page, displayNames));
    }

    private Map<UUID, String> resolveDisplayNames(ContentEntry entry) {
        Set<UUID> userIds = Stream.of(entry.getCreatedBy(), entry.getUpdatedBy())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userDisplayNameProvider.resolveDisplayNames(userIds);
    }

    private Map<UUID, String> resolveDisplayNamesForEntries(List<ContentEntry> entries) {
        Set<UUID> userIds = entries.stream()
                .flatMap(e -> Stream.of(e.getCreatedBy(), e.getUpdatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userDisplayNameProvider.resolveDisplayNames(userIds);
    }

    private UUID resolveAppId(String appSlug) {
        AppEntity app = appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        return app.getId();
    }

    private List<SortCriterion> parseSortParam(@Nullable String sort) {
        if (sort == null || sort.isBlank()) {
            return List.of();
        }

        List<SortCriterion> result = new ArrayList<>();
        for (String part : sort.split(",")) {
            String[] tokens = part.trim().split(":");
            String column = tokens[0].trim();
            SortCriterion.Direction direction = tokens.length > 1
                    && "desc".equalsIgnoreCase(tokens[1].trim())
                    ? SortCriterion.Direction.DESC
                    : SortCriterion.Direction.ASC;
            result.add(new SortCriterion(column, direction));
        }
        return result;
    }
}
