package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppDependency;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppDependencyEntity;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.DependencyGraphResponse;
import io.webcontify.generated.model.DependencyImpactResponse;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.util.Collections;
import java.util.List;
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
class AppDependencyServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @Mock
    private AppDependencyRepository dependencyRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private AppService appService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private io.webcontify.identity.service.PermissionService permissionService;

    private AppDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        dependencyService = new AppDependencyService(
                dependencyRepository, appRepository, appService, changeRecordService, permissionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAddDependency() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appB = createApp(appBId, "App B", "app-b", AppStatus.ACTIVE);

        when(appService.getById(appAId)).thenReturn(appA);
        when(appService.getById(appBId)).thenReturn(appB);
        when(dependencyRepository.existsByAppIdAndDependsOnId(appAId, appBId)).thenReturn(false);
        when(dependencyRepository.findByAppId(appBId)).thenReturn(Collections.emptyList());

        AppDependencyEntity savedEntity = new AppDependencyEntity();
        savedEntity.setAppId(appAId);
        savedEntity.setDependsOnId(appBId);
        savedEntity.setTenantId(TEST_TENANT_ID);
        when(dependencyRepository.save(any(AppDependencyEntity.class))).thenReturn(savedEntity);

        AppEntity appAEntity = new AppEntity();
        appAEntity.setId(appAId);
        appAEntity.setStatus("ACTIVE");
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appAId)).thenReturn(java.util.Optional.of(appAEntity));

        AppDependency result = dependencyService.addDependency(appAId, appBId);

        assertThat(result.getAppId()).isEqualTo(appAId);
        assertThat(result.getDependsOnId()).isEqualTo(appBId);
        assertThat(result.getDependsOnAppName()).isEqualTo("App B");
        assertThat(result.getDependsOnAppSlug()).isEqualTo("app-b");
        assertThat(result.isDirect()).isTrue();
        verify(dependencyRepository).save(any(AppDependencyEntity.class));
        verify(changeRecordService).create(eq("app"), eq(appAId), eq("updated"), contains("app-b"), anyString(), eq("draft"));
        assertThat(appAEntity.isHasDraft()).isTrue();
    }

    @Test
    void shouldRejectSelfReference() {
        UUID appId = UUID.randomUUID();

        assertThatThrownBy(() -> dependencyService.addDependency(appId, appId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("app.dependency.self_reference");
    }

    @Test
    void shouldRejectCyclicDependency() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appB = createApp(appBId, "App B", "app-b", AppStatus.ACTIVE);

        when(appService.getById(appBId)).thenReturn(appB);
        when(appService.getById(appAId)).thenReturn(appA);
        when(dependencyRepository.existsByAppIdAndDependsOnId(appBId, appAId)).thenReturn(false);

        // A→B exists. Now try B→A.
        // wouldCreateCycle(B, A): BFS from A, follow A's deps → finds B → cycle!
        AppDependencyEntity aToBEntity = new AppDependencyEntity();
        aToBEntity.setAppId(appAId);
        aToBEntity.setDependsOnId(appBId);
        when(dependencyRepository.findByAppId(appAId)).thenReturn(List.of(aToBEntity));

        assertThatThrownBy(() -> dependencyService.addDependency(appBId, appAId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("app.dependency.circular");
    }

    @Test
    void shouldRejectTransitiveCycle() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();
        UUID appCId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appC = createApp(appCId, "App C", "app-c", AppStatus.ACTIVE);

        when(appService.getById(appCId)).thenReturn(appC);
        when(appService.getById(appAId)).thenReturn(appA);
        when(dependencyRepository.existsByAppIdAndDependsOnId(appCId, appAId)).thenReturn(false);

        // Chain: A→B→C exists. Try C→A.
        // wouldCreateCycle(C, A): BFS from A, follow deps: A→B, B→C → found C == fromAppId → cycle!
        AppDependencyEntity aToBEntity = new AppDependencyEntity();
        aToBEntity.setAppId(appAId);
        aToBEntity.setDependsOnId(appBId);

        AppDependencyEntity bToCEntity = new AppDependencyEntity();
        bToCEntity.setAppId(appBId);
        bToCEntity.setDependsOnId(appCId);

        when(dependencyRepository.findByAppId(appAId)).thenReturn(List.of(aToBEntity));
        when(dependencyRepository.findByAppId(appBId)).thenReturn(List.of(bToCEntity));

        assertThatThrownBy(() -> dependencyService.addDependency(appCId, appAId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("app.dependency.circular");
    }

    @Test
    void shouldRemoveDependency() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();

        App appB = createApp(appBId, "App B", "app-b", AppStatus.ACTIVE);
        when(appService.getById(appBId)).thenReturn(appB);
        when(dependencyRepository.existsByAppIdAndDependsOnId(appAId, appBId)).thenReturn(true);

        AppEntity appAEntity = new AppEntity();
        appAEntity.setId(appAId);
        appAEntity.setStatus("ACTIVE");
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appAId)).thenReturn(java.util.Optional.of(appAEntity));

        dependencyService.removeDependency(appAId, appBId);

        verify(dependencyRepository).deleteByAppIdAndDependsOnId(appAId, appBId);
        verify(changeRecordService).create(eq("app"), eq(appAId), eq("updated"), contains("app-b"), anyString(), eq("draft"));
        assertThat(appAEntity.isHasDraft()).isTrue();
    }

    @Test
    void shouldListDirectAndTransitiveDependencies() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();
        UUID appCId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appB = createApp(appBId, "App B", "app-b", AppStatus.ACTIVE);
        App appC = createApp(appCId, "App C", "app-c", AppStatus.ACTIVE);

        when(appService.getById(appAId)).thenReturn(appA);
        when(appService.getById(appBId)).thenReturn(appB);
        when(appService.getById(appCId)).thenReturn(appC);

        // A→B direct, B→C direct
        AppDependencyEntity aToBEntity = new AppDependencyEntity();
        aToBEntity.setAppId(appAId);
        aToBEntity.setDependsOnId(appBId);

        AppDependencyEntity bToCEntity = new AppDependencyEntity();
        bToCEntity.setAppId(appBId);
        bToCEntity.setDependsOnId(appCId);

        when(dependencyRepository.findByAppId(appAId)).thenReturn(List.of(aToBEntity));
        when(dependencyRepository.findByAppId(appBId)).thenReturn(List.of(bToCEntity));
        when(dependencyRepository.findByAppId(appCId)).thenReturn(Collections.emptyList());

        List<AppDependency> result = dependencyService.listDependencies(appAId);

        assertThat(result).hasSize(2);

        AppDependency directDep = result.stream()
                .filter(d -> d.getDependsOnId().equals(appBId))
                .findFirst().orElseThrow();
        assertThat(directDep.isDirect()).isTrue();
        assertThat(directDep.getDependsOnAppName()).isEqualTo("App B");

        AppDependency transitiveDep = result.stream()
                .filter(d -> d.getDependsOnId().equals(appCId))
                .findFirst().orElseThrow();
        assertThat(transitiveDep.isDirect()).isFalse();
        assertThat(transitiveDep.getDependsOnAppName()).isEqualTo("App C");
    }

    @Test
    void shouldBuildDependencyGraph() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appB = createApp(appBId, "App B", "app-b", AppStatus.ACTIVE);

        when(appService.getById(appAId)).thenReturn(appA);
        when(appService.getById(appBId)).thenReturn(appB);

        AppDependencyEntity aToBEntity = new AppDependencyEntity();
        aToBEntity.setAppId(appAId);
        aToBEntity.setDependsOnId(appBId);

        when(dependencyRepository.findByAppId(appAId)).thenReturn(List.of(aToBEntity));
        when(dependencyRepository.findByAppId(appBId)).thenReturn(Collections.emptyList());
        when(permissionService.check(any(io.webcontify.identity.model.CheckPermissionRequest.class)))
                .thenReturn(true);

        DependencyGraphResponse graph = dependencyService.getDependencyGraph(appAId);

        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getEdges()).hasSize(1);

        assertThat(graph.getNodes())
                .anyMatch(n -> n.getId().equals(appAId) && Boolean.TRUE.equals(n.getIsCurrent()));
        assertThat(graph.getNodes())
                .anyMatch(n -> n.getId().equals(appBId) && !Boolean.TRUE.equals(n.getIsCurrent()));

        assertThat(graph.getEdges().get(0).getSource()).isEqualTo(appAId);
        assertThat(graph.getEdges().get(0).getTarget()).isEqualTo(appBId);
        assertThat(graph.getEdges().get(0).getDirect()).isTrue();
    }

    @Test
    void shouldRejectDependencyOnDraftApp() {
        UUID appAId = UUID.randomUUID();
        UUID appBId = UUID.randomUUID();

        App appA = createApp(appAId, "App A", "app-a", AppStatus.ACTIVE);
        App appB = createApp(appBId, "App B", "app-b", AppStatus.DRAFT);

        when(appService.getById(appAId)).thenReturn(appA);
        when(appService.getById(appBId)).thenReturn(appB);

        assertThatThrownBy(() -> dependencyService.addDependency(appAId, appBId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("app.dependency.target_not_active");
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
}
