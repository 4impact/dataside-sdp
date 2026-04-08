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
