package io.webcontify.identity.service;

import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.UserDisplayNameProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserDisplayNameProviderImpl implements UserDisplayNameProvider {

    private static final String DELETED_USER_FALLBACK = "Deleted User";

    private final TenantUserRepository userRepository;

    public UserDisplayNameProviderImpl(TenantUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Map<UUID, String> resolveDisplayNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> nonNullIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (nonNullIds.isEmpty()) {
            return Map.of();
        }

        List<TenantUserEntity> users = userRepository.findAllById(nonNullIds);

        Map<UUID, String> result = new HashMap<>();
        for (TenantUserEntity user : users) {
            result.put(user.getId(), user.getFirstName() + " " + user.getLastName());
        }

        // Fallback for IDs not found (deleted users)
        for (UUID id : nonNullIds) {
            result.putIfAbsent(id, DELETED_USER_FALLBACK);
        }

        return result;
    }
}
