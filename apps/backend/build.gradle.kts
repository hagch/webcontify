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

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jooq:jooq")

    // MapStruct
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // UUID v7
    implementation("com.fasterxml.uuid:java-uuid-generator:$uuidGeneratorVersion")

    // OpenAPI generated code dependencies
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.43")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}

// OpenAPI Code Generation
val openApiSpecDir = "${rootProject.projectDir}/../../openapi"
val openApiOutputDir = "${layout.buildDirectory.get()}/generated/openapi"

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$openApiSpecDir/health-api.yaml")
    outputDir.set(openApiOutputDir)
    apiPackage.set("io.webcontify.generated.api")
    modelPackage.set("io.webcontify.generated.model")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "false",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "generateBuilders" to "false",
        "additionalModelTypeAnnotations" to ""
    ))
    typeMappings.set(mapOf(
        "DateTime" to "java.time.OffsetDateTime"
    ))
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

tasks.withType<Test> {
    useJUnitPlatform()
}
