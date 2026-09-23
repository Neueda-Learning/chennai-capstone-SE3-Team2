# Trade REST API. Multi-stage: the runtime stage carries a JRE and one
# jar, and nothing that could compile or download anything.
#
# Build context is the repository root, because the service depends on
# the sibling domain module:  docker build -f services/trade-api/Dockerfile .

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Poms before sources: dependencies change less often, so editing a
# .java file reuses this cached layer instead of re-resolving.
COPY pom.xml .
COPY libs/domain/pom.xml libs/domain/
COPY services/trade-api/pom.xml services/trade-api/
COPY services/trade-executor/pom.xml services/trade-executor/

# -B batch mode, and go-offline over the reactor so every module's
# dependencies land in the same cached layer.
RUN mvn -B -q dependency:go-offline

# Sources second.
COPY libs/domain/src libs/domain/src
COPY services/trade-api/src services/trade-api/src
COPY services/trade-executor/src services/trade-executor/src

# -pl services/trade-api -am: build the API and also make the modules it needs,
# which is the domain. One invocation, in dependency order.
#
# Tests are not run here. The integration suite needs a database, and a
# build that reaches for one has made the image depend on
# infrastructure. `mvn clean install` at the root is the gate, and it
# runs in CI and on a developer machine before an image is ever built.
RUN mvn -B -DskipTests -pl services/trade-api -am package \
 && mv services/trade-api/target/trade-api-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl

# Not root: this process reads a jar and opens sockets, nothing more.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8080

# Healthy, not merely running. start-period covers JVM startup and the
# first database connection.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# exec form so the JVM is PID 1 and gets SIGTERM directly.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
