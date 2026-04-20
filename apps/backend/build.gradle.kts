import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.20.0"
}

group = "io.webcontify"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springModulithVersion = "2.0.3"
val mapstructVersion = "1.6.3"
val uuidGeneratorVersion = "5.2.0"
val testcontainersVersion = "1.20.4"
val awsSdkVersion = "2.31.1"

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
        mavenBom("software.amazon.awssdk:bom:$awsSdkVersion")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")

    // MapStruct
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // UUID v7
    implementation("com.fasterxml.uuid:java-uuid-generator:$uuidGeneratorVersion")

    // AWS S3
    implementation("software.amazon.awssdk:s3")

    // Security — OAuth2 Resource Server with JWT (Keycloak)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // OpenFGA Java SDK
    implementation("dev.openfga:openfga-sdk:0.7.2")

    // Keycloak Admin Client
    implementation("org.keycloak:keycloak-admin-client:26.0.0")

    // Caffeine Cache
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // AOP for @RequiresPermission (included in spring-boot-starter in Spring Boot 4)
    implementation("org.springframework:spring-aspects")

    // Structured Logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // OpenAPI generated code dependencies
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.annotation:jakarta.annotation-api")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.2")
}

// OpenAPI Code Generation — Multi-Spec
val openApiSpecDir = "${rootProject.projectDir}/../../openapi"
val openApiOutputDir = "${layout.buildDirectory.get()}/generated/openapi"

val openApiConfigOptions = mapOf(
    "interfaceOnly" to "true",
    "useSpringBoot3" to "true",
    "useTags" to "true",
    "skipDefaultInterface" to "false",
    "openApiNullable" to "false",
    "documentationProvider" to "none",
    "generateBuilders" to "false",
    "additionalModelTypeAnnotations" to ""
)

val openApiTypeMappings = mapOf(
    "DateTime" to "java.time.OffsetDateTime"
)

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$openApiSpecDir/tenant-api.yaml")
    outputDir.set(openApiOutputDir)
    apiPackage.set("io.webcontify.generated.api")
    modelPackage.set("io.webcontify.generated.model")
    configOptions.set(openApiConfigOptions)
    typeMappings.set(openApiTypeMappings)
}

sourceSets {
    main {
        java {
            srcDir("$openApiOutputDir/src/main/java")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.register<Copy>("syncOpenFgaModel") {
    from(file("${projectDir}/../../infra/openfga/model.fga"))
    into("src/main/resources/openfga/")
}
tasks.named("processResources") { dependsOn("syncOpenFgaModel") }

tasks.withType<Test> {
    useJUnitPlatform()
}
