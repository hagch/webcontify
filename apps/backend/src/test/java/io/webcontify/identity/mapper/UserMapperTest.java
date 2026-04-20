package io.webcontify.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.identity.model.TenantUser;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.model.AuditInfo;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.OffsetDateTime;
import java.util.UUID;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void shouldMapEntityToDomain() {
        var entity = new TenantUserEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setEmail("test@test.com");
        entity.setFirstName("Max");
        entity.setLastName("Muster");
        entity.setStatus(UserStatus.ACTIVE);
        entity.setInvitedBy(UUID.randomUUID());
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(UUID.randomUUID());
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(UUID.randomUUID());
        entity.setAuditInfo(auditInfo);

        TenantUser domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.getId());
        assertThat(domain.getEmail()).isEqualTo("test@test.com");
        assertThat(domain.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(domain.getCreatedAt()).isEqualTo(auditInfo.getCreatedAt());
    }

    @Test
    void shouldMapDomainToResponse() {
        var domain = new TenantUser();
        domain.setId(UUID.randomUUID());
        domain.setEmail("test@test.com");
        domain.setFirstName("Max");
        domain.setLastName("Muster");
        domain.setStatus(UserStatus.INVITED);
        domain.setCreatedAt(OffsetDateTime.now());
        domain.setUpdatedAt(OffsetDateTime.now());

        var response = mapper.toResponse(domain);

        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getStatus().getValue()).isEqualTo("INVITED");
    }
}
