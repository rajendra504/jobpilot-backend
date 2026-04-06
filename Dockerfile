FROM eclipse-temurin:21-jdk

# Install Playwright system deps
RUN apt-get update && apt-get install -y \
    libxcursor1 libgtk-3-0 libpangocairo-1.0-0 \
    libcairo-gobject2 libgdk-pixbuf-2.0-0 \
    libnss3 libatk1.0-0 libatk-bridge2.0-0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
    libgbm1 libasound2t64 && \
    rm -rf /var/lib/apt/lists/*

COPY . .

RUN mvn package -DskipTests

# Pre-download Playwright browsers
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install chromium" || true

CMD ["java", "-jar", "target/jobpilot-backend.jar"]