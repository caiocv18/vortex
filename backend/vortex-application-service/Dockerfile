# 1) Build: JDK 27 and the module's Maven Wrapper (Maven 3.10.0).
# The stage runs on the builder's own platform: the jar is the same for every target, so a
# multi-platform build compiles once and never runs Maven under emulation
FROM --platform=$BUILDPLATFORM eclipse-temurin:27-jdk AS build

WORKDIR /app

# The wrapper and pom.xml first, so the dependencies stay in their own cached layer
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Sources, then the jar
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# 2) Runtime: only the JRE 27 (Alpine) and the jar. No RUN step, so the arm64 image is
# assembled without executing anything for that platform
FROM eclipse-temurin:27-jre-alpine AS runtime

WORKDIR /app
# Owned by root and world-readable: the process can read the jar but never rewrite it
COPY --from=build /app/target/*.jar app.jar

# Light-mode flags by default (override JAVA_OPTS to change them).
# SerialGC is explicit because JDK 27 picks G1 even with little memory
ENV MALLOC_ARENA_MAX=2 \
    JAVA_OPTS="-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m -Xmx320m"

EXPOSE 8080
# Unprivileged, the same UID as the authorization service image; the app writes only to /tmp
USER 185
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
