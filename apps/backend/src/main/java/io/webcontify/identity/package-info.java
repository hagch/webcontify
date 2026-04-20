@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared",
        "shared::shared-persistence",
        "shared::shared-security",
        "tenant::tenant-service",
        "tenant::tenant-model",
        "tenant::tenant-events",
        "tenant::tenant-repository",
        "tenant::tenant-entity"
    }
)
package io.webcontify.identity;
