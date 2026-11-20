# =====================================================================
# FitJanta: container image for cloud hosting (Railway / Render / any Docker host)
# =====================================================================

# ---- 1. Build the Spring Boot jar ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Gradle wrapper + build files first (cached between builds)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon --version

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- 2. Small runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# The platform sets PORT; 8080 is the default
ENV PORT=8080
EXPOSE 8080

# Keep memory use within free-tier limits
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["java", "-jar", "app.jar"]
