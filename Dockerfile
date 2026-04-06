# ---------- STAGE 1: Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests


# ---------- STAGE 2: Runtime ----------
FROM eclipse-temurin:21-jdk

ENV DEBIAN_FRONTEND=noninteractive

# Install Playwright dependencies
RUN apt-get update && apt-get install -y \
    libxcursor1 libgtk-3-0 libpangocairo-1.0-0 \
    libcairo-gobject2 libgdk-pixbuf-2.0-0 \
    libnss3 libatk1.0-0 libatk-bridge2.0-0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
    libgbm1 libasound2t64 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy only the built JAR (not full project)
COPY --from=builder /app/target/*.jar app.jar

# Pre-download Playwright browsers
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium || true

CMD ["java", "-jar", "app.jar"]