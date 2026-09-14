# Build context is the repo root (see compose.yaml). Copy the whole backend tree:
# the reactor lists every module, so all module poms must be present even though
# only this module is packaged (-am builds required modules from source).
# Network is available at build time; tests are skipped here (CI runs
# module-scoped ./scripts/mvnw.sh test instead).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend ./backend
RUN mvn -f backend/pom.xml -pl taskmesh-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/taskmesh-server/target/taskmesh-server-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
