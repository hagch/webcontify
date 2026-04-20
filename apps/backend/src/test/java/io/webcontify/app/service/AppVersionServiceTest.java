package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.generated.model.AppProblemsResponse;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantSlugResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_APP_ID = UUID.randomUUID();

    @Mock
    private AppRepository appRepository;

    @Mock
    private AppVersionRepository appVersionRepository;

    @Mock
    private AppVersionMapper appVersionMapper;

    @Mock
    private AppSnapshotService appSnapshotService;

    @Mock
    private AppDependencyService dependencyService;

    @Mock
    private AppService appService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private TenantSlugResolver tenantSlugResolver;

    @Mock
    private AppValidationService validationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppVersionService appVersionService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        appVersionService = new AppVersionService(
                appRepository,
                appVersionRepository,
                appVersionMapper,
                appSnapshotService,
                dependencyService,
                appService,
                validationService,
                changeRecordService,
                tenantSlugResolver,
                eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPublishFirstVersion() {
        App app = createApp(TEST_APP_ID, "Test App", "test-app", AppStatus.DRAFT);
        when(appService.getById(TEST_APP_ID)).thenReturn(app);
        when(validationService.validate(TEST_APP_ID)).thenReturn(emptyProblems());
        when(appVersionRepository.existsByAppIdAndVersion(TEST_APP_ID, "0.1.0")).thenReturn(false);
        when(tenantSlugResolver.resolveSlugByTenantId(TEST_TENANT_ID)).thenReturn(Optional.of("test-tenant"));

        AppSnapshot snapshot = new AppSnapshot(
                TEST_APP_ID.toString(), "0.1.0",
                new AppSnapshot.AppSnapshotMetadata("Test App", "test-app", null),
                List.of(), List.of());
        when(appSnapshotService.buildSnapshot(app, "0.1.0")).thenReturn(snapshot);
        when(appSnapshotService.uploadSnapshot("tenant-test-tenant", snapshot))
                .thenReturn("files/snapshots/app-" + TEST_APP_ID + "-v0.1.0.json");

        AppEntity appEntity = createAppEntity(TEST_APP_ID, "Test App", "test-app", "DRAFT");
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_APP_ID)).thenReturn(Optional.of(appEntity));
        when(appRepository.save(appEntity)).thenReturn(appEntity);

        AppVersionEntity savedEntity = new AppVersionEntity();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setAppId(TEST_APP_ID);
        savedEntity.setVersion("0.1.0");
        savedEntity.setPublishedAt(OffsetDateTime.now());
        when(appVersionRepository.save(any(AppVersionEntity.class))).thenReturn(savedEntity);

        AppVersion expectedVersion = new AppVersion();
        expectedVersion.setId(savedEntity.getId());
        expectedVersion.setVersion("0.1.0");
        when(appVersionMapper.toDomain(savedEntity)).thenReturn(expectedVersion);

        AppVersion result = appVersionService.publish(TEST_APP_ID, "0.1.0", "Initial release");

        assertThat(result.getVersion()).isEqualTo("0.1.0");
        assertThat(appEntity.getStatus()).isEqualTo("ACTIVE");
        assertThat(appEntity.isHasDraft()).isFalse();
        verify(changeRecordService).closeDraftRecords("app", TEST_APP_ID);

        ArgumentCaptor<AppPublishedEvent> eventCaptor = ArgumentCaptor.forClass(AppPublishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appId()).isEqualTo(TEST_APP_ID);
        assertThat(eventCaptor.getValue().version()).isEqualTo("0.1.0");
    }

    @Test
    void shouldRejectDuplicateVersion() {
        App app = createApp(TEST_APP_ID, "Test App", "test-app", AppStatus.ACTIVE);
        when(appService.getById(TEST_APP_ID)).thenReturn(app);
        when(validationService.validate(TEST_APP_ID)).thenReturn(emptyProblems());
        when(appVersionRepository.existsByAppIdAndVersion(TEST_APP_ID, "1.0.0")).thenReturn(true);

        assertThatThrownBy(() -> appVersionService.publish(TEST_APP_ID, "1.0.0", "Duplicate"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("app.version.already_exists");
    }

    @Test
    void shouldListVersionsInDescendingOrder() {
        when(appService.getById(TEST_APP_ID)).thenReturn(createApp(TEST_APP_ID, "App", "app", AppStatus.ACTIVE));

        AppVersionEntity entity1 = new AppVersionEntity();
        entity1.setId(UUID.randomUUID());
        entity1.setVersion("1.1.0");
        entity1.setPublishedAt(OffsetDateTime.now());

        AppVersionEntity entity2 = new AppVersionEntity();
        entity2.setId(UUID.randomUUID());
        entity2.setVersion("1.0.0");
        entity2.setPublishedAt(OffsetDateTime.now().minusDays(1));

        when(appVersionRepository.findByAppIdOrderByPublishedAtDesc(TEST_APP_ID))
                .thenReturn(List.of(entity1, entity2));

        AppVersion v1 = new AppVersion();
        v1.setVersion("1.1.0");
        AppVersion v2 = new AppVersion();
        v2.setVersion("1.0.0");
        when(appVersionMapper.toDomain(entity1)).thenReturn(v1);
        when(appVersionMapper.toDomain(entity2)).thenReturn(v2);

        List<AppVersion> versions = appVersionService.listVersions(TEST_APP_ID);

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo("1.1.0");
        assertThat(versions.get(1).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldThrowNotFoundForMissingVersion() {
        UUID versionId = UUID.randomUUID();
        when(appVersionRepository.findByIdAndAppId(versionId, TEST_APP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appVersionService.getVersion(TEST_APP_ID, versionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("app.version.not_found");
    }

    @Test
    void shouldRollbackAndSetHasDraft() {
        UUID versionId = UUID.randomUUID();

        AppVersionEntity versionEntity = new AppVersionEntity();
        versionEntity.setId(versionId);
        versionEntity.setAppId(TEST_APP_ID);
        versionEntity.setVersion("1.0.0");
        when(appVersionRepository.findByIdAndAppId(versionId, TEST_APP_ID))
                .thenReturn(Optional.of(versionEntity));

        AppVersion version = new AppVersion();
        version.setId(versionId);
        version.setVersion("1.0.0");
        when(appVersionMapper.toDomain(versionEntity)).thenReturn(version);

        AppEntity appEntity = createAppEntity(TEST_APP_ID, "App", "app", "ACTIVE");
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_APP_ID)).thenReturn(Optional.of(appEntity));
        when(appRepository.save(appEntity)).thenReturn(appEntity);

        when(appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(TEST_APP_ID))
                .thenReturn(Optional.of(versionEntity));

        App updatedApp = createApp(TEST_APP_ID, "App", "app", AppStatus.ACTIVE);
        updatedApp.setHasDraft(true);
        when(appService.getById(TEST_APP_ID)).thenReturn(updatedApp);

        App result = appVersionService.rollback(TEST_APP_ID, versionId);

        assertThat(appEntity.isHasDraft()).isTrue();
        verify(changeRecordService).create(eq("app"), eq(TEST_APP_ID), eq("ROLLBACK"), eq("{}"),
                eq("VERSION_ROLLBACK"), eq("1.0.1"));
    }

    @Test
    void shouldSuggestFirstVersion() {
        when(appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(TEST_APP_ID))
                .thenReturn(Optional.empty());

        String suggested = appVersionService.suggestNextVersion(TEST_APP_ID);

        assertThat(suggested).isEqualTo("0.1.0");
    }

    @Test
    void shouldSuggestIncrementedPatchVersion() {
        AppVersionEntity latest = new AppVersionEntity();
        latest.setVersion("1.0.0");
        when(appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(TEST_APP_ID))
                .thenReturn(Optional.of(latest));

        String suggested = appVersionService.suggestNextVersion(TEST_APP_ID);

        assertThat(suggested).isEqualTo("1.0.1");
    }

    @Test
    void shouldBlockPublishWhenValidationFails() {
        App app = createApp(TEST_APP_ID, "Test App", "test-app", AppStatus.DRAFT);
        when(appService.getById(TEST_APP_ID)).thenReturn(app);

        AppProblemsResponse problems = new AppProblemsResponse();
        problems.setItems(List.of(new io.webcontify.generated.model.AppProblem(
                io.webcontify.generated.model.AppProblem.SeverityEnum.ERROR,
                "Dependency 'X' is inactive", "app:123", "Activate or remove")));
        problems.setErrorCount(1);
        problems.setWarningCount(0);
        when(validationService.validate(TEST_APP_ID)).thenReturn(problems);

        assertThatThrownBy(() -> appVersionService.publish(TEST_APP_ID, "0.1.0", "Release"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("app.publish.has_problems");
    }

    private AppProblemsResponse emptyProblems() {
        AppProblemsResponse response = new AppProblemsResponse();
        response.setItems(List.of());
        response.setErrorCount(0);
        response.setWarningCount(0);
        return response;
    }

    private App createApp(UUID id, String name, String slug, AppStatus status) {
        App app = new App();
        app.setId(id);
        app.setTenantId(TEST_TENANT_ID);
        app.setName(name);
        app.setSlug(slug);
        app.setStatus(status);
        return app;
    }

    private AppEntity createAppEntity(UUID id, String name, String slug, String status) {
        AppEntity entity = new AppEntity();
        entity.setId(id);
        entity.setTenantId(TEST_TENANT_ID);
        entity.setName(name);
        entity.setSlug(slug);
        entity.setStatus(status);
        entity.setHasDraft(false);
        return entity;
    }
}
