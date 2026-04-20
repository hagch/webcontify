@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "shared",
            "shared::shared-changelog",
            "shared::shared-security",
            "shared::shared-persistence",
            "shared::shared-util",
            "app"
        })
package io.webcontify.content;
