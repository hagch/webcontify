import nu.studer.gradle.jooq.JooqGenerate
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.testcontainers.containers.PostgreSQLContainer

buildscript {
  repositories {
    mavenCentral()
    mavenLocal()
  }
  dependencies { classpath("org.testcontainers:postgresql:1.19.1") }
}

plugins {
  id("jacoco")
  id("org.springframework.boot") version "3.2.0"
  id("io.spring.dependency-management") version "1.1.4"
  id("nu.studer.jooq") version "8.2.1"
  id("com.diffplug.spotless") version "6.21.0"
  id("org.flywaydb.flyway") version "9.22.1"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
  kotlin("kapt") version "1.9.21"
  application
}

group = "io.webcontify"

version = "0.0.1-SNAPSHOT"

java { sourceCompatibility = JavaVersion.VERSION_20 }

configurations { compileOnly { extendsFrom(configurations.annotationProcessor.get()) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-jooq:3.1.0")
  implementation("org.springframework.boot:spring-boot-starter-web:3.1.0")
  implementation("org.springframework.boot:spring-boot-starter-validation:3.0.4")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
  implementation("org.springframework.modulith:spring-modulith-starter-core:1.1.0")
  implementation("org.mapstruct:mapstruct:1.5.5.Final")
  implementation("org.flywaydb:flyway-core:9.16.0")
  kapt("org.mapstruct:mapstruct-processor:1.5.5.Final")
  compileOnly("org.projectlombok:lombok:1.18.26")
  developmentOnly("org.springframework.boot:spring-boot-docker-compose:3.1.1")
  runtimeOnly("org.postgresql:postgresql:42.5.4")
  jooqGenerator("org.postgresql:postgresql:42.5.4")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.1.0")
  testImplementation("org.springframework.modulith:spring-modulith-starter-test:1.1.0")
  testImplementation("org.springframework.boot:spring-boot-testcontainers:3.2.0")
  testImplementation("org.testcontainers:junit-jupiter:1.19.1")
  testImplementation("org.testcontainers:postgresql:1.19.1")
  implementation("org.jooq:jooq-jackson-extensions:3.18.0")
  testImplementation("io.mockk:mockk:1.13.8")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.modulith:spring-modulith-bom:1.0.0")
    mavenBom("org.testcontainers:testcontainers-bom:1.19.1")
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs += "-Xjsr305=strict"
    jvmTarget = "20"
  }
}

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
