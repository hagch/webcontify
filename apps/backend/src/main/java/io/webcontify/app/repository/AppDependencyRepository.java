package io.webcontify.app.repository;

import io.webcontify.app.repository.entity.AppDependencyEntity;
import io.webcontify.app.repository.entity.AppDependencyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppDependencyRepository extends JpaRepository<AppDependencyEntity, AppDependencyId> {
    List<AppDependencyEntity> findByAppId(UUID appId);
    List<AppDependencyEntity> findByDependsOnId(UUID dependsOnId);
    boolean existsByAppIdAndDependsOnId(UUID appId, UUID dependsOnId);
    void deleteByAppIdAndDependsOnId(UUID appId, UUID dependsOnId);
}
