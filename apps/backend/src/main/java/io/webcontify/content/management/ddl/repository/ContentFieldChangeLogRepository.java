package io.webcontify.content.management.ddl.repository;

import io.webcontify.content.management.ddl.repository.entity.ContentFieldChangeLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentFieldChangeLogRepository
        extends JpaRepository<ContentFieldChangeLogEntity, UUID> {}
