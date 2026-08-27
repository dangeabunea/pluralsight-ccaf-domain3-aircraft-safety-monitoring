# Stage 1: build
# Build context is the project root (set in docker-compose.yml) so all module
# sources and pom.xml files are reachable from here.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy the root reactor pom first — defines the multi-module structure.
COPY pom.xml .

# Copy every module's pom.xml before touching source code.
# Docker caches this layer until any pom.xml changes; the expensive
# dependency:go-offline step below is skipped on source-only edits.
COPY backend/atcsafety-contracts/pom.xml backend/atcsafety-contracts/pom.xml
COPY backend/radar-data-processing/pom.xml backend/radar-data-processing/pom.xml
COPY backend/separation-infringement-detection/pom.xml backend/separation-infringement-detection/pom.xml
COPY backend/rest-api/pom.xml backend/rest-api/pom.xml

# Resolve all transitive dependencies from the root reactor so the local cache
# is fully populated before source compilation.
RUN mvn dependency:go-offline -B

# Copy all backend sources after the dependency cache layer is established.
COPY backend/ backend/

# Build only the radar service and its upstream contracts library.
# -am (also-make) ensures atcsafety-contracts is compiled first.
RUN mvn -pl backend/atcsafety-contracts,backend/radar-data-processing -am package -DskipTests -B

# Stage 2: runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/backend/radar-data-processing/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
