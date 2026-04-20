package io.webcontify.app.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.shared.AppProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppProviderImpl implements AppProvider {

    private final AppRepository appRepository;

    public AppProviderImpl(AppRepository appRepository) {
        this.appRepository = appRepository;
    }

    @Override
    public List<AppInfo> findAllActiveApps() {
        return appRepository.findByAuditInfoDeletedAtIsNull(Pageable.unpaged())
                .getContent()
                .stream()
                .map(entity -> new AppInfo(entity.getId(), entity.getName(), entity.getSlug()))
                .toList();
    }
}
