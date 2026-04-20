package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.AppRevisionResponse;
import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AppRevisionServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_APP_ID = UUID.randomUUID();
    private static final UUID TEST_REVISION_ID = UUID.randomUUID();

    @Mock private ChangeRecordService changeRecordService;
    @Mock private AppService appService;
    @Mock private AppRepository appRepository;
    @Mock private AppMapper appMapper;

    private AppRevisionService appRevisionService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, "test-user", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        appRevisionService = new AppRevisionService(changeRecordService, appService, appRepository, appMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListDraftRevisions() {
        App app = new App();
        app.setId(TEST_APP_ID);
        when(appService.getById(TEST_APP_ID)).thenReturn(app);

        ChangeRecord record = createChangeRecord(
                TEST_REVISION_ID,
                "updated",
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"New Name\",\"fromValue\":\"Old Name\"}]",
                "user:" + TEST_TENANT_ID);
        when(changeRecordService.getDraftRecords("app", TEST_APP_ID)).thenReturn(List.of(record));

        List<AppRevisionResponse> result = appRevisionService.listRevisions(TEST_APP_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(TEST_REVISION_ID);
        assertThat(result.get(0).getChangeType()).isEqualTo("updated");
        assertThat(result.get(0).getSource()).isEqualTo("user:" + TEST_TENANT_ID);
        assertThat(result.get(0).getSummary()).isEqualTo("Changed name");
    }

    @Test
    void shouldGetRevisionById() {
        ChangeRecord record = createChangeRecord(
                TEST_REVISION_ID,
                "updated",
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"New\",\"fromValue\":\"Old\"}]",
                "user:" + TEST_TENANT_ID);
        when(changeRecordService.getById(TEST_REVISION_ID)).thenReturn(record);

        AppRevisionResponse result = appRevisionService.getRevision(TEST_REVISION_ID);

        assertThat(result.getId()).isEqualTo(TEST_REVISION_ID);
        assertThat(result.getChangeType()).isEqualTo("updated");
    }

    @Test
    void shouldRevertRevision() {
        ChangeRecord record = createChangeRecord(
                TEST_REVISION_ID,
                "updated",
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"New Name\",\"fromValue\":\"Old Name\"}]",
                "user:" + TEST_TENANT_ID);
        when(changeRecordService.getById(TEST_REVISION_ID)).thenReturn(record);

        AppEntity entity = createAppEntity("New Name", "test-slug", "DRAFT");
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_APP_ID))
                .thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        App revertedApp = new App();
        revertedApp.setId(TEST_APP_ID);
        revertedApp.setName("Old Name");
        when(appMapper.toDomain(any(AppEntity.class))).thenReturn(revertedApp);

        when(changeRecordService.create(anyString(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ChangeRecord());

        App result = appRevisionService.revertRevision(TEST_APP_ID, TEST_REVISION_ID);

        assertThat(result.getName()).isEqualTo("Old Name");
        verify(appRepository).save(any(AppEntity.class));
        verify(changeRecordService).create(eq("app"), eq(TEST_APP_ID), eq("reverted"), anyString(),
                eq("system:revert"), eq("draft"));
    }

    @Test
    void shouldThrowNotFoundForMissingRevision() {
        when(changeRecordService.getById(TEST_REVISION_ID))
                .thenThrow(new NotFoundException("change_record.not_found"));

        assertThatThrownBy(() -> appRevisionService.getRevision(TEST_REVISION_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("change_record.not_found");
    }

    @Test
    void shouldGenerateHumanReadableSummary() {
        String payload =
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"New\"},{\"op\":\"replace\",\"path\":\"/description\",\"value\":\"Updated\"}]";

        String summary = appRevisionService.generateSummary(payload);

        assertThat(summary).isEqualTo("Changed name, Changed description");
    }

    private ChangeRecord createChangeRecord(UUID id, String changeType, String payload, String source) {
        ChangeRecord record = new ChangeRecord();
        record.setId(id);
        record.setTenantId(TEST_TENANT_ID);
        record.setEntityType("app");
        record.setEntityId(TEST_APP_ID);
        record.setChangeType(changeType);
        record.setPayload(payload);
        record.setSource(source);
        record.setDraftVersion("draft");
        record.setCreatedAt(OffsetDateTime.now());
        record.setCreatedBy(TEST_TENANT_ID);
        return record;
    }

    private AppEntity createAppEntity(String name, String slug, String status) {
        AppEntity entity = new AppEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TEST_TENANT_ID);
        entity.setName(name);
        entity.setSlug(slug);
        entity.setDescription("");
        entity.setStatus(status);
        entity.setHasDraft(false);

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(TEST_TENANT_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(TEST_TENANT_ID);
        entity.setAuditInfo(auditInfo);

        return entity;
    }
}
