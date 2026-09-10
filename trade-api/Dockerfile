# =====================================================================
# Trade REST API, multi-stage build.
#
# The reason to care about the stages is not disk. Every tool in an
# image is a tool available to whoever gets into the container: a JDK
# ships a compiler and a class loader, Maven ships a downloader and
# your settings.xml, and the build cache ships whatever your
# dependencies contained. The runtime stage below carries a Java
# runtime and one jar, and nothing that could build or fetch anything.
#
# BUILD CONTEXT IS THE REPOSITORY ROOT, not this folder, because the
# service depends on the sibling domain module. Build it as:
#
#     docker build -f trade-api/Dockerfile .
#
# or let compose do it; its build block sets the context.
#
# The reactor is what makes this one Maven invocation. The domain is a
# real module with its own jar and its own coordinates, and Maven
# resolves it from the build rather than from ~/.m2, which this image
# starts empty and cannot fill from anybody's laptop.
# =====================================================================

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# The poms alone first. Dependencies change far less often than
# sources, so resolving them in their own layer means editing a .java
# file reuses the cached download instead of repeating it. Copying the
# whole tree first would invalidate this layer on every source edit.
COPY pom.xml .
COPY domainrules/pom.xml domainrules/
COPY trade-api/pom.xml   trade-api/

# -B batch mode, and go-offline over the reactor so both modules'
# dependencies land in the same cached layer.
RUN mvn -B -q dependency:go-offline

# Now the sources, in their own layer.
COPY domainrules/src domainrules/src
COPY trade-api/src   trade-api/src

# -pl trade-api -am: build the API and also make the modules it needs,
# which is the domain. One invocation, in dependency order.
#
# Tests are not run here. The integration suite needs a database, and a
# build that reaches for one has made the image depend on
# infrastructure. `mvn clean install` at the root is the gate, and it
# runs in CI and on a developer machine before an image is ever built.
RUN mvn -B -DskipTests -pl trade-api -am package \
 && mv trade-api/target/trade-api-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl for the health check. Alpine's busybox wget would also do, but
# curl reports a non-2xx status as a failure without extra shell.
RUN apk add --no-cache curl

# Not root. A process that only needs to read a jar and open a socket
# has no reason to write to the filesystem or install anything, and
# this is the difference between a container escape being an incident
# and being a catastrophe.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8080

# The orchestrator should report this service as healthy, not merely
# running. start-period covers JVM startup and the first connection to
# Postgres without counting those seconds as failures.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# exec form: the JVM becomes PID 1 and receives SIGTERM directly, so
# `docker compose down` shuts the pool down cleanly instead of waiting
# out a ten-second kill timer.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
