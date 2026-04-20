package io.webcontify.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.shared.model.SystemUser;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuditListenerTest {

    private final AuditListener listener = new AuditListener();

    @Test
    void prePersistShouldFillAllAuditFields() {
        TestEntity entity = new TestEntity();

        listener.prePersist(entity);

        assertThat(entity.getAuditInfo().getCreatedAt()).isNotNull();
        assertThat(entity.getAuditInfo().getCreatedBy()).isEqualTo(SystemUser.ID);
        assertThat(entity.getAuditInfo().getUpdatedAt()).isNotNull();
        assertThat(entity.getAuditInfo().getUpdatedBy()).isEqualTo(SystemUser.ID);
        assertThat(entity.getAuditInfo().getCreatedAt())
                .isEqualTo(entity.getAuditInfo().getUpdatedAt());
    }

    @Test
    void preUpdateShouldOnlyUpdateUpdatedFields() {
        TestEntity entity = new TestEntity();
        listener.prePersist(entity);

        OffsetDateTime originalCreatedAt = entity.getAuditInfo().getCreatedAt();

        listener.preUpdate(entity);

        assertThat(entity.getAuditInfo().getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(entity.getAuditInfo().getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
        assertThat(entity.getAuditInfo().getUpdatedBy()).isEqualTo(SystemUser.ID);
    }

    private static class TestEntity extends AuditableBaseEntity {}
}
