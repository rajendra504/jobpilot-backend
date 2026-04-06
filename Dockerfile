# Stage 1 — Build the JAR
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2 — Run the JAR
FROM eclipse-temurin:21-jre

# Install Node.js (required by Playwright CLI to install browsers)
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Install Playwright CLI globally so we can run `playwright install`
RUN npm install -g playwright

# Install Chromium + its system dependencies via Playwright
# This ensures the exact browser binary Playwright Java expects is present
RUN playwright install chromium --with-deps

WORKDIR /app
COPY --from=build /app/target/jobpilot-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]