package helpers.setups.repository

import helpers.setups.TestContainerSetup
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.jooq.JooqTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension

@JooqTest
@Sql("/cleanup.sql")
@ExtendWith(SpringExtension::class)
@Import(JooqTestConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JooqTestSetup : TestContainerSetup()
