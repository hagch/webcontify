import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.1.4"
	id("io.spring.dependency-management") version "1.1.3"
	id("nu.studer.jooq") version "8.2"
	kotlin("jvm") version "1.8.22"
	kotlin("plugin.spring") version "1.8.22"
}

group = "io.webcontify"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

jooq {
	configurations {
		create("main") {
			jooqConfiguration.apply {
				logging = org.jooq.meta.jaxb.Logging.WARN
				jdbc.apply {
					driver = project.properties["datasourceDriver"].toString()
					url = project.properties["datasourceJdbcUrl"].toString()
					user = project.properties["datasourceUsername"].toString()
					password = project.properties["datasourcePassword"].toString()
				}
				generator.apply {
					name = "org.jooq.codegen.KotlinGenerator"
					database.apply {
						inputSchema = "public"
						name = "org.jooq.meta.postgres.PostgresDatabase"
						includes = ".*"
						excludes = ""
					}
					target.apply {
						packageName = "io.webcontify.backend.jooq"
						directory = "build/generated/sources/jooq"
					}
					generate.apply {
						isDaos = true
						isPojosAsKotlinDataClasses = true
						isSpringDao = true
						isSpringAnnotations = true
					}

				}
			}
		}
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	jooqGenerator("org.postgresql:postgresql:42.5.1")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:1.0.0")
	}
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<ProcessResources> {
	filesMatching("application.yaml") {
		expand(project.properties)
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
