# Gradle + Spring Boot 4.0.3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `sdp-sink-connector` from Maven to Gradle (Kotlin DSL) and upgrade Spring Boot from 3.5.5 to 4.0.3 in a single atomic change.

**Architecture:** Write `settings.gradle.kts` and `build.gradle.kts` as direct replacements for `pom.xml`, generate the Gradle wrapper, update `Dockerfile` to use Gradle instead of Maven, update `CLAUDE.md` build commands, then delete `pom.xml`. No application source code changes.

**Tech Stack:** Gradle 8.14 (Kotlin DSL), Spring Boot 4.0.3, `io.spring.dependency-management` 1.1.7, Java 25 toolchain

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `settings.gradle.kts` | Create | Project identity (`rootProject.name`) |
| `build.gradle.kts` | Create | Plugins, Java toolchain, all dependencies, test config |
| `gradle/wrapper/gradle-wrapper.properties` | Create | Gradle 8.14 download URL and paths |
| `gradlew` | Generate | Unix wrapper bootstrap script |
| `gradlew.bat` | Generate | Windows wrapper bootstrap script |
| `gradle/wrapper/gradle-wrapper.jar` | Generate | Binary bootstrap JAR |
| `Dockerfile` | Modify | Replace Maven install + `target/` path with Gradle wrapper + `build/libs/` |
| `CLAUDE.md` | Modify | Update command reference from `mvn` to `./gradlew` equivalents |
| `pom.xml` | Delete | Replaced by `build.gradle.kts` |

---

### Task 1: Create `settings.gradle.kts`

**Files:**
- Create: `settings.gradle.kts`

- [ ] **Step 1: Write the file**

```kotlin
rootProject.name = "sdp-sink-connector"
```

- [ ] **Step 2: Commit**

```bash
git add settings.gradle.kts
git commit -m "build: add Gradle settings file"
```

---

### Task 2: Create `build.gradle.kts`

**Files:**
- Create: `build.gradle.kts`

- [ ] **Step 1: Write the file**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

Key mapping notes vs `pom.xml`:
- Lombok `<optional>true</optional>` → `compileOnly` + `annotationProcessor` (also `testCompileOnly` + `testAnnotationProcessor` so Lombok works in test classes)
- `logstash-logback-encoder:8.1` and `wiremock-standalone:3.10.0` are pinned because they are not in the Spring Boot BOM
- All other versions are managed by the `io.spring.dependency-management` plugin via the Spring Boot 4.0.3 BOM

- [ ] **Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add Gradle Kotlin DSL build file with Spring Boot 4.0.3"
```

---

### Task 3: Generate Gradle wrapper

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Generate: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`

- [ ] **Step 1: Write `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Generate the wrapper scripts and JAR**

Run (requires Gradle installed globally — check with `gradle --version`):

```bash
gradle wrapper --gradle-version 8.14
```

Expected output:
```
BUILD SUCCESSFUL in Xs
1 actionable task: 1 executed
```

This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.

**If `gradle` is not installed:** Open the project directory in IntelliJ IDEA. IntelliJ detects `build.gradle.kts` and offers to import it — accepting the import generates all wrapper files automatically.

- [ ] **Step 3: Commit wrapper files**

```bash
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
git commit -m "build: add Gradle 8.14 wrapper"
```

---

### Task 4: Update `Dockerfile`

**Files:**
- Modify: `Dockerfile`

The current Dockerfile installs Maven and copies from `target/`. Replace with the Gradle wrapper and copy from `build/libs/`.

- [ ] **Step 1: Replace the entire `Dockerfile` content**

```dockerfile
# Build stage
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

COPY settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY gradlew ./
COPY src ./src

RUN chmod +x gradlew && ./gradlew -q build -x test

# Runtime stage
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/build/libs/sdp-sink-connector-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

Changes from original:
- Copies `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, and `gradlew` instead of `pom.xml`
- Runs `./gradlew -q build -x test` instead of `apk add maven && mvn -B -q package -DskipTests`
- Copies from `build/libs/sdp-sink-connector-*.jar` instead of `target/sdp-sink-connector-*.jar`
- No `apk add maven` — Gradle wrapper is self-bootstrapping

- [ ] **Step 2: Commit**

```bash
git add Dockerfile
git commit -m "build: update Dockerfile to use Gradle wrapper instead of Maven"
```

---

### Task 5: Update `CLAUDE.md` build commands

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Replace the Commands section in `CLAUDE.md`**

Replace the existing `## Commands` block (lines 3–16) with:

```markdown
## Commands

```bash
# Build (skip tests)
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.fourimpact.sdpsinkconnector.service.MessageProcessingServiceTest"

# Run a single test method
./gradlew test --tests "com.fourimpact.sdpsinkconnector.transformer.MessageToSdpTransformerTest.toCreatePayload_mapsAllFields"

# Run the application locally (requires Kafka + env vars set)
./gradlew bootRun --args='--spring.profiles.active=local'

# Build the Docker image
docker build -t sdp-sink-connector .
```

On Windows use `gradlew.bat` instead of `./gradlew`.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md commands from Maven to Gradle"
```

---

### Task 6: Delete `pom.xml`

**Files:**
- Delete: `pom.xml`

- [ ] **Step 1: Remove via git**

```bash
git rm pom.xml
git commit -m "build: remove Maven pom.xml (replaced by Gradle)"
```

---

### Task 7: Verify build and tests

- [ ] **Step 1: Run the full build**

On Windows:
```bash
gradlew.bat build
```

On Unix/WSL:
```bash
./gradlew build
```

Expected:
```
BUILD SUCCESSFUL in Xs
7 actionable tasks: 7 executed
```

- [ ] **Step 2: Run just the tests**

On Windows:
```bash
gradlew.bat test
```

On Unix/WSL:
```bash
./gradlew test
```

Expected: All tests pass. The test suite has ~30+ unit tests across:
- `MessageToSdpTransformerTest`
- `MessageProcessingServiceTest`
- `ServiceDeskPlusClientImplTest` (WireMock-based)
- `SdpKafkaConsumerTest`
- `ZohoOAuthClientTest`
- `OAuthAuthStrategyTest`

Test report is written to `build/reports/tests/test/index.html`.

- [ ] **Step 3: If any tests fail**, run with `--info` to get verbose output:

```bash
gradlew.bat test --info
```

Common issues after a Spring Boot major version migration:
- Removed or relocated auto-configuration classes → check `build/reports/tests/` for the full stack trace
- Changed default property names → add `spring-boot-properties-migrator` temporarily to `testImplementation` in `build.gradle.kts` to get runtime warnings
