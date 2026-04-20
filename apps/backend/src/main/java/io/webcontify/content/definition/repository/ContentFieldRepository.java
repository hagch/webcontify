package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContentFieldRepository extends JpaRepository<ContentFieldEntity, UUID> {

    Optional<ContentFieldEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);

    List<ContentFieldEntity> findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(
            UUID definitionId);

    boolean existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull(
            String fieldKey, UUID definitionId);

    boolean existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull(
            String fieldKey, UUID definitionId, UUID id);

    int countByDefinitionIdAndAuditInfoDeletedAtIsNull(UUID definitionId);

    List<ContentFieldEntity> findByDefinitionIdAndPendingActionIsNotNull(UUID definitionId);

    @Query(
            value = "SELECT * FROM content_field_definition f WHERE f.field_type = 'REFERENCE'"
                    + " AND f.config @> CAST(:targetJson AS jsonb) AND f.deleted_at IS NULL",
            nativeQuery = true)
    List<ContentFieldEntity> findReferenceFieldsByTargetDefinitionId(String targetJson);

    @Query(
            value = "SELECT * FROM content_field_definition f WHERE f.field_type = 'MIRROR'"
                    + " AND f.config @> CAST(:sourceJson AS jsonb) AND f.deleted_at IS NULL",
            nativeQuery = true)
    List<ContentFieldEntity> findMirrorFieldsBySourceFieldId(String sourceJson);
}
