package helpers.setups.repository

import helpers.setups.TestContainerSetup
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension

@JooqTest
@ExtendWith(SpringExtension::class)
@Import(JooqTestConfiguration::class)
@ActiveProfiles(profiles = ["test"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JooqTestSetup : TestContainerSetup()
