# =====================================================================
# Trade Executor, multi-stage build.
#
# Same shape and the same reasoning as trade-api/Dockerfile: the build
# stage carries a JDK, Maven and a dependency cache, and the runtime
# stage carries a Java runtime and one jar, with nothing that could
# build or fetch anything.
#
# BUILD CONTEXT IS THE REPOSITORY ROOT, not this folder, because the
# executor depends on the sibling domain module -- Account.canAfford is
# business rule 6 and Position.canSell is rule 7, and the executor
# re-checks both at execution time rather than keeping a second copy.
# Build it as:
#
#     docker build -f sprint-07-event-backbone/executor/Dockerfile .
#
# or let compose do it; its build block sets the context.
# =====================================================================

# --------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Poms first, in their own layer: dependencies change far less often
# than sources, so editing a .java file reuses the cached download.
COPY pom.xml .
COPY domainrules/pom.xml domainrules/
COPY trade-api/pom.xml   trade-api/
COPY sprint-07-event-backbone/executor/pom.xml sprint-07-event-backbone/executor/

RUN mvn -B -q dependency:go-offline

# Sources. trade-api's are copied because the reactor lists it as a
# module and Maven will not build the tree without them, even though
# -pl below never builds it.
COPY domainrules/src domainrules/src
COPY trade-api/src   trade-api/src
COPY sprint-07-event-backbone/executor/src sprint-07-event-backbone/executor/src

# -pl the executor -am: build it and the modules it needs, which is the
# domain. One invocation, in dependency order.
#
# Tests are not run here. The settlement suite wants a PostgreSQL, and
# a build that reaches for one has made the image depend on
# infrastructure. `mvn clean install` at the root is the gate.
RUN mvn -B -DskipTests -pl sprint-07-event-backbone/executor -am package \
 && mv sprint-07-event-backbone/executor/target/trade-executor-*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl

# Not root. This process reads a jar, opens sockets to Postgres, Kafka
# and one HTTPS host, and writes nothing.
RUN addgroup -S trade && adduser -S -G trade trade

WORKDIR /app
COPY --from=build --chown=trade:trade /build/app.jar /app/app.jar

USER trade

EXPOSE 8081

# Healthy, not merely running. start-period covers JVM startup, the
# first Postgres connection and the consumer group joining.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# exec form, so the JVM is PID 1 and gets SIGTERM directly: the
# consumer then leaves its group cleanly instead of the broker waiting
# out a session timeout and rebalancing twice.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "/app/app.jar"]
