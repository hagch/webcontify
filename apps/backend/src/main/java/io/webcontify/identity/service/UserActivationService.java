package io.webcontify.identity.service;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserActivationService {

    private static final Logger log = LoggerFactory.getLogger(UserActivationService.class);

    private final TenantUserRepository userRepository;

    public UserActivationService(TenantUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void activateOnFirstLogin(UUID userId) {
        userRepository.findById(userId).ifPresent(entity -> {
            if (entity.getStatus() == UserStatus.INVITED && entity.getAuditInfo().getDeletedAt() == null) {
                entity.setStatus(UserStatus.ACTIVE);
                entity.getAuditInfo().setUpdatedAt(OffsetDateTime.now());
                entity.getAuditInfo().setUpdatedBy(userId);
                userRepository.save(entity);
                log.info("User activated on first login: id={}", userId);
            }
        });
    }
}
