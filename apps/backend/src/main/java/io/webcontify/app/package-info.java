@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared",
        "shared::shared-changelog",
        "shared::shared-events",
        "shared::shared-persistence",
        "shared::shared-security",
        "shared::shared-util",
        "identity::identity-service",
        "identity::identity-security",
        "identity::identity-model"
    }
)
package io.webcontify.app;
