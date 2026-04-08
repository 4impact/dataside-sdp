import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.0.3"
}

group = "com.fourimpact"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.retry:spring-retry:2.0.12")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
