# Trade Executor. Same shape as the Trade REST API image: the runtime
# stage carries a JRE and one jar and nothing else.
#
# Build context is the repository root:
#   docker build -f Services/executor-service/Dockerfile .

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Poms before sources, for the layer cache.
COPY pom.xml .
COPY Services/domain-library/pom.xml Services/domain-library/
COPY Services/order-service/pom.xml Services/order-service/
COPY Services/executor-service/pom.xml Services/executor-service/

RUN mvn -B -q dependency:go-offline

# The reactor needs every module present, even ones this image never packages.
COPY Services/domain-library/src Services/domain-library/src
COPY Services/order-service/src Services/order-service/src
COPY Services/executor-service/src Services/executor-service/src

# Tests skipped: the settlement suite needs a database, and an image
# build must not depend on infrastructure.
RUN mvn -B -DskipTests -pl Services/executor-service -am package \
 && mv Services/executor-service/target/trade-executor-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl

# Not root.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8082

# Healthy, not merely running. start-period covers the consumer joining its group.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1

# exec form so the consumer leaves its group cleanly on shutdown.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
