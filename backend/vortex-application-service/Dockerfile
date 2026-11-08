# 1) Build: JDK 27 + o Maven Wrapper do módulo (Maven 3.10.0)
FROM eclipse-temurin:27-jdk AS build

WORKDIR /app

# Só o wrapper e o pom.xml primeiro, para cachear as dependências
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# código-fonte e empacota
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# 2) Runtime: só a JRE 27 (Alpine), com o jar
FROM eclipse-temurin:27-jre-alpine AS runtime

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Flags do modo leve como padrão (sobrescreva JAVA_OPTS para mudar):
# SerialGC explícito porque o JDK 27 escolhe G1 mesmo com pouca RAM
ENV MALLOC_ARENA_MAX=2 \
    JAVA_OPTS="-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m -Xmx320m"

EXPOSE 8080
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
