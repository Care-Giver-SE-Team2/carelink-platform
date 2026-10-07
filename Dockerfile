# All-in-one local image: the front-end build output is packaged into the core jar, so one
# container runs the whole application. Used by docker-compose.yml and the caregiver demo.
# Cloud images are built per service from build/Dockerfile; the front end is served from S3.

# ---------- 1. Build the front end ----------
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2. Build the backend ----------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src
# Copy only the pom and wrapper first so the dependency layer stays cacheable
COPY services/core/.mvn/ .mvn/
COPY services/core/mvnw services/core/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY services/core/src ./src
# Put the front-end output in static so Spring serves it directly
COPY --from=frontend /app/dist ./src/main/resources/static
# The hand-written OpenAPI contract lives at repo root, not in services/core/; the pom
# resolves it via a relative path (../docs/api), so it must land one level above WORKDIR.
# Both the final contract and the superseded draft are copied - the pom picks up
# whichever of the two it's configured to include.
COPY docs/api/openapi.yaml docs/api/openapi-draft.yaml /docs/api/
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------- 3. Runtime ----------
FROM eclipse-temurin:25-jre
# curl is needed for the container health check; the base image does not ship it, and
# without it the compose healthcheck can never pass. Also create a non-root user to run
# the application, as a minimum container-security measure.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --create-home carelink
WORKDIR /app
COPY --from=backend /src/target/*.jar app.jar
RUN chown carelink:carelink /app/app.jar
# The commit this image was built from, exposed at /actuator/info so a deployment
# can be verified from outside ("is staging running what I just pushed?").
ARG GIT_SHA=unknown
ENV APP_COMMIT=$GIT_SHA
USER carelink
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
