package io.webcontify.app.repository;

import io.webcontify.app.repository.entity.AppVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, UUID> {

    List<AppVersionEntity> findByAppIdOrderByPublishedAtDesc(UUID appId);

    Optional<AppVersionEntity> findByIdAndAppId(UUID id, UUID appId);

    Optional<AppVersionEntity> findTopByAppIdOrderByPublishedAtDesc(UUID appId);

    boolean existsByAppIdAndVersion(UUID appId, String version);
}
