plugins {
	java
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "com.alpian"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Payment Service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
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

extra["snippetsDir"] = file("build/generated-snippets")

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
	implementation("org.mapstruct:mapstruct:1.5.5.Final")
	implementation("com.github.kagkarlsson:db-scheduler-spring-boot-starter:14.0.3")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-restdocs")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.assertj:assertj-core:3.27.7")
	testImplementation("org.awaitility:awaitility:4.2.0")
	testImplementation("io.cucumber:cucumber-java:7.20.1")
	testImplementation("io.cucumber:cucumber-spring:7.20.1")
	testImplementation("io.cucumber:cucumber-junit-platform-engine:7.20.1")
	testImplementation("org.junit.platform:junit-platform-suite")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation("org.testcontainers:testcontainers:2.0.3")
	testImplementation("org.testcontainers:postgresql:1.21.4")
	testImplementation("org.testcontainers:kafka:1.21.4")
	testImplementation("org.testcontainers:junit-jupiter:1.21.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<JavaCompile>("compileJava") {
	options.compilerArgs.addAll(listOf(
		"-Amapstruct.defaultComponentModel=spring",
		"-Amapstruct.unmappedTargetPolicy=IGNORE"
	))
}

tasks.test {
	outputs.dir(project.extra["snippetsDir"]!!)
}

tasks.asciidoctor {
	inputs.dir(project.extra["snippetsDir"]!!)
	dependsOn(tasks.test)
}
