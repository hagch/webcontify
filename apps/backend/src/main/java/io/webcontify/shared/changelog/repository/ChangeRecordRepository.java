package io.webcontify.shared.changelog.repository;

import io.webcontify.shared.changelog.repository.entity.ChangeRecordEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeRecordRepository extends JpaRepository<ChangeRecordEntity, UUID> {

    List<ChangeRecordEntity> findByEntityTypeAndEntityIdAndDraftVersionIsNotNullOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    List<ChangeRecordEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    @Modifying
    @Query(
            "UPDATE ChangeRecordEntity c SET c.draftVersion = null "
                    + "WHERE c.entityType = :entityType AND c.entityId = :entityId "
                    + "AND c.draftVersion IS NOT NULL")
    void closeDraftRecords(@Param("entityType") String entityType, @Param("entityId") UUID entityId);

    List<ChangeRecordEntity> findByEntityTypeAndEntityIdInAndDraftVersionIsNotNullOrderByCreatedAtDesc(
            String entityType, List<UUID> entityIds);
}
