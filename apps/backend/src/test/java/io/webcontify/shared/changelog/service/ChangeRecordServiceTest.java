package io.webcontify.shared.changelog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.repository.ChangeRecordRepository;
import io.webcontify.shared.changelog.repository.entity.ChangeRecordEntity;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ChangeRecordServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @Mock
    private ChangeRecordRepository changeRecordRepository;

    private ChangeRecordService changeRecordService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        changeRecordService = new ChangeRecordService(changeRecordRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateChangeRecord() {
        UUID entityId = UUID.randomUUID();
        String payload = "{\"name\":\"Test\"}";

        when(changeRecordRepository.save(any(ChangeRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChangeRecord result =
                changeRecordService.create("APP", entityId, "UPDATE", payload, "api", "v1-draft");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(result.getEntityType()).isEqualTo("APP");
        assertThat(result.getEntityId()).isEqualTo(entityId);
        assertThat(result.getChangeType()).isEqualTo("UPDATE");
        assertThat(result.getPayload()).isEqualTo(payload);
        assertThat(result.getSource()).isEqualTo("api");
        assertThat(result.getDraftVersion()).isEqualTo("v1-draft");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getCreatedBy()).isEqualTo(TEST_USER_ID);

        ArgumentCaptor<ChangeRecordEntity> captor = ArgumentCaptor.forClass(ChangeRecordEntity.class);
        verify(changeRecordRepository).save(captor.capture());
        ChangeRecordEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(savedEntity.getEntityType()).isEqualTo("APP");
    }

    @Test
    void shouldCloseDraftRecords() {
        UUID entityId = UUID.randomUUID();

        changeRecordService.closeDraftRecords("APP", entityId);

        verify(changeRecordRepository).closeDraftRecords("APP", entityId);
    }

    @Test
    void shouldReturnDraftRecords() {
        UUID entityId = UUID.randomUUID();

        ChangeRecordEntity entity1 = createEntity("APP", entityId, "CREATE", "v1-draft");
        ChangeRecordEntity entity2 = createEntity("APP", entityId, "UPDATE", "v1-draft");

        when(changeRecordRepository
                        .findByEntityTypeAndEntityIdAndDraftVersionIsNotNullOrderByCreatedAtDesc(
                                "APP", entityId))
                .thenReturn(List.of(entity1, entity2));

        List<ChangeRecord> results = changeRecordService.getDraftRecords("APP", entityId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getChangeType()).isEqualTo("CREATE");
        assertThat(results.get(1).getChangeType()).isEqualTo("UPDATE");
        assertThat(results.get(0).getDraftVersion()).isEqualTo("v1-draft");
    }

    private ChangeRecordEntity createEntity(
            String entityType, UUID entityId, String changeType, String draftVersion) {
        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TEST_TENANT_ID);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setChangeType(changeType);
        entity.setPayload("{\"test\":true}");
        entity.setSource("api");
        entity.setDraftVersion(draftVersion);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setCreatedBy(TEST_TENANT_ID);
        return entity;
    }
}
