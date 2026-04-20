package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentDefinitionRepository extends JpaRepository<ContentDefinitionEntity, UUID> {

    Optional<ContentDefinitionEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);

    List<ContentDefinitionEntity> findByAppIdAndAuditInfoDeletedAtIsNull(UUID appId);

    List<ContentDefinitionEntity> findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
            Collection<UUID> ids, UUID appId);

    boolean existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(String name, UUID appId);

    boolean existsBySlugAndAppIdAndAuditInfoDeletedAtIsNull(String slug, UUID appId);

    Optional<ContentDefinitionEntity> findByNameAndAppIdAndAuditInfoDeletedAtIsNull(
            String name, UUID appId);

    boolean existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull(
            String name, UUID appId, UUID id);

    Optional<ContentDefinitionEntity> findBySlugAndAppIdAndAuditInfoDeletedAtIsNull(
            String slug, UUID appId);
}
