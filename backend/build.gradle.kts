import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.testcontainers.containers.PostgreSQLContainer

buildscript {
  repositories {
    mavenCentral()
    mavenLocal()
  }
  dependencies {
    classpath("org.testcontainers:postgresql:1.20.1")
    classpath("org.flywaydb:flyway-database-postgresql:10.17.1")
  }
}

plugins {
  id("jacoco")
  id("org.springframework.boot") version "3.3.2"
  id("io.spring.dependency-management") version "1.1.6"
  id("nu.studer.jooq") version "9.0"
  id("com.diffplug.spotless") version "6.25.0"
  id("org.flywaydb.flyway") version "10.17.1"
  kotlin("jvm") version "2.0.10"
  kotlin("plugin.spring") version "2.0.10"
  kotlin("kapt") version "2.0.10"
  application
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.modulith:spring-modulith-bom:1.2.2")
    mavenBom("org.testcontainers:testcontainers-bom:1.20.1")
  }
}

group = "io.webcontify"

version = "0.0.1-SNAPSHOT"

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

configurations { compileOnly { extendsFrom(configurations.annotationProcessor.get()) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-jooq")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.modulith:spring-modulith-starter-core")
  developmentOnly("org.springframework.boot:spring-boot-docker-compose")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.modulith:spring-modulith-starter-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
  implementation(kotlin("reflect"))
  implementation("org.mapstruct:mapstruct:1.6.0")
  kapt("org.mapstruct:mapstruct-processor:1.6.0")

  implementation("org.flywaydb:flyway-core:10.17.1")
  runtimeOnly("org.flywaydb:flyway-database-postgresql:10.17.1")

  implementation("org.jooq:jooq-jackson-extensions:3.19.10")
  jooqGenerator("org.postgresql:postgresql:42.7.3")
  runtimeOnly("org.postgresql:postgresql:42.7.3")

  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:postgresql")

  testImplementation("io.mockk:mockk:1.13.12")

  testImplementation("io.rest-assured:spring-mock-mvc-kotlin-extensions:5.5.0")
}

tasks.withType<KotlinCompile> { kotlin.jvmToolchain(21) }

tasks.withType<ProcessResources> {
  filesMatching("application.yaml") { expand(project.properties) }
}

tasks.withType<Test> { useJUnitPlatform() }

task("postgresContainer") {
  val container =
      PostgreSQLContainer<Nothing>("postgres:latest").apply { withDatabaseName("jooq-generation") }
  container.start()

  rootProject.extra["postgresContainerUrl"] = container.jdbcUrl
  rootProject.extra["postgresContainerUserName"] = container.username
  rootProject.extra["postgresContainerPassword"] = container.password
  rootProject.extra["postgresContainerDatabaseName"] = container.databaseName
  rootProject.extra["postgresContainer"] = container
}

flyway {
  locations = arrayOf("filesystem:src/main/resources/db/migration")
  url = rootProject.extra["postgresContainerUrl"] as String
  user = rootProject.extra["postgresContainerUserName"] as String
  password = rootProject.extra["postgresContainerPassword"] as String
}

spotless {
  format("misc") {
    target("*.gradle", "*.md", ".gitignore")
    trimTrailingWhitespace()
    indentWithTabs()
    endWithNewline()
  }
  kotlin {
    targetExclude("build/**", "**.sql")
    ktfmt()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
  }
}

tasks.withType<JooqGenerate> {
  dependsOn("postgresContainer", "flywayMigrate")
  inputs
      .files(fileTree("src/main/resources/db/migration"))
      .withPropertyName("migrations")
      .withPathSensitivity(PathSensitivity.RELATIVE)
  doLast { (rootProject.extra["postgresContainer"] as PostgreSQLContainer<*>).stop() }
}

jooq {
  version.set("3.19.10")
  edition = nu.studer.gradle.jooq.JooqEdition.OSS
  configurations {
    create("main") {
      jooqConfiguration.apply {
        logging = org.jooq.meta.jaxb.Logging.WARN
        jdbc.apply {
          driver = project.properties["datasourceDriver"].toString()
          url = rootProject.extra["postgresContainerUrl"].toString()
          user = rootProject.extra["postgresContainerUserName"].toString()
          password = rootProject.extra["postgresContainerPassword"].toString()
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            inputSchema = "public"
            name = "org.jooq.meta.postgres.PostgresDatabase"
            includes = "WEBCONTIFY_.*"
            excludes = ""
          }
          target.apply {
            packageName = "io.webcontify.backend.jooq"
            directory = "build/generated/sources/jooq"
          }
        }
      }
    }
  }
}

jacoco {
  toolVersion = "0.8.11"
  reportsDirectory = layout.buildDirectory.dir("reports/jacoco")
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }

tasks.jacocoTestReport { dependsOn(tasks.test) }
