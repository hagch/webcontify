package io.webcontify.app.listener;

import io.webcontify.app.service.AppService;
import io.webcontify.shared.event.ContentDefinitionChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AppContentListener {

    private static final Logger log = LoggerFactory.getLogger(AppContentListener.class);

    private final AppService appService;

    public AppContentListener(AppService appService) {
        this.appService = appService;
    }

    @ApplicationModuleListener
    public void onContentDefinitionChanged(ContentDefinitionChangedEvent event) {
        log.info("Content definition changed in app={}, marking as draft", event.appId());
        appService.markAsDraft(event.appId());
    }
}
