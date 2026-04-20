@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "shared",
        "shared::shared-persistence",
        "shared::shared-security",
        "shared::shared-util"
    }
)
package io.webcontify.tenant;
