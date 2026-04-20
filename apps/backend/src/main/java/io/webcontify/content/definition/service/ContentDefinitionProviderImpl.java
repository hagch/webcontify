package io.webcontify.content.definition.service;

import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.shared.ContentDefinitionProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ContentDefinitionProviderImpl implements ContentDefinitionProvider {

    private final ContentDefinitionRepository definitionRepository;

    public ContentDefinitionProviderImpl(ContentDefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
    }

    @Override
    public List<DefinitionInfo> findDefinitionsByAppId(UUID appId) {
        return definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId).stream()
                .map(e -> new DefinitionInfo(e.getId(), e.getName()))
                .toList();
    }
}
