# 1) Build: JDK 27 + Maven
FROM eclipse-temurin:27-jdk AS build

# instala o Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# só pom.xml para cache de dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B

# código-fonte e empacota
COPY src ./src
RUN mvn clean package -DskipTests

# 2) Runtime: JDK 27 Alpine, só com o jar
FROM amazoncorretto:27-alpine3.22-jdk AS runtime

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
