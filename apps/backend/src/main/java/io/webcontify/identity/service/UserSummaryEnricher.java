package io.webcontify.identity.service;

import io.webcontify.generated.model.AppVersionResponsePublishedByUser;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserSummaryEnricher {

    private final TenantUserRepository userRepository;

    public UserSummaryEnricher(TenantUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppVersionResponsePublishedByUser resolve(UUID userId) {
        if (userId == null) return null;
        return userRepository.findActiveById(userId)
                .map(this::toSummary)
                .orElse(null);
    }

    public Map<UUID, AppVersionResponsePublishedByUser> resolveAll(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Set<UUID> uniqueIds = new HashSet<>(userIds);
        uniqueIds.remove(null);
        if (uniqueIds.isEmpty()) return Map.of();
        return userRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(TenantUserEntity::getId, this::toSummary));
    }

    private AppVersionResponsePublishedByUser toSummary(TenantUserEntity entity) {
        var summary = new AppVersionResponsePublishedByUser();
        summary.setId(entity.getId());
        summary.setFirstName(entity.getFirstName());
        summary.setLastName(entity.getLastName());
        summary.setEmail(entity.getEmail());
        return summary;
    }
}
