#!/bin/sh
# The start-up every service shares, so all of them run with the same JVM settings and stop
# the same way. Configuration comes from environment variables (12-Factor); JAVA_OPTS adds
# JVM flags for one service or one environment without changing this file.
set -eu

# On SIGTERM, Spring Boot stops taking new requests and finishes the ones in flight. The
# pod's terminationGracePeriodSeconds must cover this plus the preStop pause (see the chart).
export SERVER_SHUTDOWN="${SERVER_SHUTDOWN:-graceful}"
export SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE="${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:-20s}"

# exec, so the JVM replaces this shell and receives SIGTERM from Kubernetes directly.
#   MaxRAMPercentage        heap sized from the container's memory limit, not the node's
#   ExitOnOutOfMemoryError  a JVM out of memory exits and is restarted, not left half-alive
#   user.timezone=UTC       the same zone in every container; the database connection
#                           declares its own zone in DB_URL
#   inetaddr.ttl            look DNS names up again every 30 s, so a database failover
#                           (same name, new address) is picked up within seconds. Failed
#                           lookups keep the JDK's own 10 s (java.security sets it, and
#                           there it takes precedence over a system property)
# shellcheck disable=SC2086  # JAVA_OPTS is a list of flags and must split into words
exec java \
  -XX:MaxRAMPercentage=75 \
  -XX:+ExitOnOutOfMemoryError \
  -Duser.timezone=UTC \
  -Dsun.net.inetaddr.ttl=30 \
  ${JAVA_OPTS:-} \
  -jar /app/app.jar "$@"
