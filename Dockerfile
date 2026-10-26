# CareerSphere AI as one image: the React app is built and bundled into the Spring Boot jar,
# so the web app and the API are served from the same address.
#
#   docker build -t careersphere .
#   docker compose up            (with MySQL, see docker-compose.yml)

# ---- 1. Build the React app ----
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
# The API is on the same address, so the app calls /api.
ENV VITE_API_BASE_URL=/api
RUN npm run build

# ---- 2. Build the backend jar with the web app inside ----
FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline || true
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

# ---- 3. Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=api /api/app.jar app.jar
# Résumés (/data/uploads), certificates and videos are saved under /data; mount a volume there.
ENV UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads /data/certificates /data/videos
# Keep memory use within small hosting plans.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
