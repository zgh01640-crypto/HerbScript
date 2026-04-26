
FROM docker.1ms.run/library/maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM docker.1ms.run/library/eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/herbscript-backend-0.1.0.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
