# syntax=docker/dockerfile:1
#
# The one Dockerfile every service is built with. The service is chosen at build time:
#
#     docker build -f build/Dockerfile --build-arg SERVICE=core --build-arg GIT_SHA=<commit> .
#
# scripts/build.sh runs exactly this and tags the image with the commit. The build context is
# the repository root, because each service's pom.xml inherits from the root pom.xml, and core
# also packages docs/api/ (see services/core/pom.xml). .dockerignore keeps everything else out.
#
# Changing this file rebuilds every service in the pipeline, so changes go through review.

# ---------- 1. Build the service jar with Maven ----------
FROM eclipse-temurin:25-jdk AS build
ARG SERVICE
RUN test -n "$SERVICE" || { echo "pass --build-arg SERVICE=<directory under services/>" >&2; exit 1; }
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/${SERVICE}/pom.xml services/${SERVICE}/
COPY docs/api/ docs/api/
COPY services/${SERVICE}/src/ services/${SERVICE}/src/
# ~/.m2 is a build cache that stays on the machine between builds, so dependencies are
# downloaded once, not again for every image. The tests have already run before the image
# is built (scripts/build.sh <service> test).
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
 && ./mvnw -B -ntp -f services/${SERVICE}/pom.xml package -DskipTests \
 && cp services/${SERVICE}/target/*.jar /src/app.jar

# ---------- 2. Run on a JRE, as a non-root user ----------
FROM eclipse-temurin:25-jre
# curl serves the container health check in docker-compose.yml; Kubernetes probes over HTTP
# itself. The user is numeric in USER below so Kubernetes can verify runAsNonRoot.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --create-home carelink
WORKDIR /app
COPY --from=build /src/app.jar app.jar
COPY --chmod=755 build/entrypoint.sh entrypoint.sh
# The commit this image was built from, shown at /actuator/info, so a deployment can be
# checked from outside ("is staging running what I just merged?").
ARG GIT_SHA=unknown
ENV APP_COMMIT=$GIT_SHA
USER 1001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
