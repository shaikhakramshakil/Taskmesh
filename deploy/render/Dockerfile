# Render build: repo root is the build context.
FROM docker.io/maven:3.9-eclipse-temurin-21 AS build
COPY backend /srcb/backend
WORKDIR /srcb
RUN mvn -f backend/pom.xml -pl taskmesh-server,taskmesh-worker -am package -DskipTests

FROM docker.io/node:22-alpine AS uibuild
COPY ui/package*.json /app/
WORKDIR /app
RUN npm ci 2>/dev/null || npm install
COPY ui/ ./
RUN npm run build

FROM docker.io/postgres:16-bookworm
COPY --from=docker.io/eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH
RUN apt-get update && apt-get install -y --no-install-recommends nginx && rm -rf /var/lib/apt/lists/*
COPY --from=build /srcb/backend/taskmesh-server/target/taskmesh-server-*.jar /app/server.jar
COPY --from=build /srcb/backend/taskmesh-worker/target/taskmesh-worker-*.jar /app/worker.jar
COPY --from=uibuild /app/dist /usr/share/nginx/html
COPY deploy/hf/nginx-space.conf /etc/nginx/conf.d/taskmesh.conf
COPY deploy/hf/start.sh /start.sh
RUN chmod +x /start.sh
CMD ["/start.sh"]
