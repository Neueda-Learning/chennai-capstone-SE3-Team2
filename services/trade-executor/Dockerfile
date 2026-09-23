# Trade Executor. Same shape as the Trade REST API image: the runtime
# stage carries a JRE and one jar and nothing else.
#
# Build context is the repository root:
#   docker build -f services/trade-executor/Dockerfile .

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Poms before sources, for the layer cache.
COPY pom.xml .
COPY libs/domain/pom.xml libs/domain/
COPY services/trade-api/pom.xml services/trade-api/
COPY services/trade-executor/pom.xml services/trade-executor/

RUN mvn -B -q dependency:go-offline

# The reactor needs every module present, even ones this image never packages.
COPY libs/domain/src libs/domain/src
COPY services/trade-api/src services/trade-api/src
COPY services/trade-executor/src services/trade-executor/src

# Tests skipped: the settlement suite needs a database, and an image
# build must not depend on infrastructure.
RUN mvn -B -DskipTests -pl services/trade-executor -am package \
 && mv services/trade-executor/target/trade-executor-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl

# Not root.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8081

# Healthy, not merely running. start-period covers the consumer joining its group.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# exec form so the consumer leaves its group cleanly on shutdown.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
