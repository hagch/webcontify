@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "shared::shared-changelog",
            "shared::shared-security",
            "shared::shared-persistence",
            "shared::shared-util",
            "shared::shared-events",
            "app",
            "app::app-events",
            "app::app-repository",
            "app::app-entity",
            "app::app-model",
            "app::app-service",
            "identity::identity-service",
            "identity::identity-security",
            "identity::identity-model"
        })
package io.webcontify.content;
