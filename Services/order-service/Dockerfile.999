# Trade REST API. Multi-stage: the runtime stage carries a JRE and one
# jar, and nothing that could compile or download anything.
#
# Build context is the repository root, because the service depends on
# the sibling domain module:  docker build -f Services/order-service/Dockerfile .

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Poms before sources: dependencies change less often, so editing a
# .java file reuses this cached layer instead of re-resolving.
COPY pom.xml .
COPY Services/domain-library/pom.xml Services/domain-library/
COPY Services/order-service/pom.xml Services/order-service/
COPY Services/executor-service/pom.xml Services/executor-service/

# -B batch mode, and go-offline over the reactor so every module's
# dependencies land in the same cached layer.
RUN mvn -B -q dependency:go-offline

# Sources second.
COPY Services/domain-library/src Services/domain-library/src
COPY Services/order-service/src Services/order-service/src
COPY Services/executor-service/src Services/executor-service/src

# -pl Services/order-service -am: build the API and also make the modules it needs,
# which is the domain. One invocation, in dependency order.
#
# Tests are not run here. The integration suite needs a database, and a
# build that reaches for one has made the image depend on
# infrastructure. `mvn clean install` at the root is the gate, and it
# runs in CI and on a developer machine before an image is ever built.
RUN mvn -B -DskipTests -pl Services/order-service -am package \
 && mv Services/order-service/target/trade-api-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl

# Not root: this process reads a jar and opens sockets, nothing more.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8081

# Healthy, not merely running. start-period covers JVM startup and the
# first database connection.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# exec form so the JVM is PID 1 and gets SIGTERM directly.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
