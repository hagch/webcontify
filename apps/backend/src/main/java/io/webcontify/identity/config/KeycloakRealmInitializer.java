package io.webcontify.identity.config;

import io.webcontify.identity.model.SmtpConfig;
import io.webcontify.identity.model.UpdateRealmSmtpRequest;
import io.webcontify.identity.service.KeycloakAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRealmInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRealmInitializer.class);

    private static final String PLATFORM_REALM = "webcontify-platform";

    private final KeycloakAdminService keycloakAdminService;
    private final PlatformSmtpProperties smtpProperties;

    public KeycloakRealmInitializer(KeycloakAdminService keycloakAdminService,
                                    PlatformSmtpProperties smtpProperties) {
        this.keycloakAdminService = keycloakAdminService;
        this.smtpProperties = smtpProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Ensuring unmanaged attribute policy is set for platform realm");
        try {
            keycloakAdminService.enableUnmanagedAttributes(PLATFORM_REALM);
        } catch (Exception e) {
            log.warn("Failed to set unmanaged attribute policy on platform realm at startup", e);
        }

        log.info("Ensuring SMTP is configured for platform realm");
        try {
            var smtpConfig = new SmtpConfig(
                    smtpProperties.host(),
                    smtpProperties.port(),
                    smtpProperties.from(),
                    smtpProperties.username(),
                    smtpProperties.password(),
                    smtpProperties.starttls()
            );
            keycloakAdminService.updateRealmSmtp(new UpdateRealmSmtpRequest(PLATFORM_REALM, smtpConfig));
        } catch (Exception e) {
            log.warn("Failed to configure SMTP on platform realm at startup", e);
        }
    }
}
