package helpers.setups.repository

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan

@TestConfiguration
@ComponentScan(
    basePackages =
        [
            "io.webcontify.backend.collections.mappers",
            "io.webcontify.backend.collections.repositories",
            "io.webcontify.backend.collections.services.field.handler"])
class JooqTestConfiguration
