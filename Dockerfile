FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG MODULE=platform-gateway
ARG PORT=8080
ENV SERVER_PORT=${SERVER_PORT:-8080}
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
EXPOSE ${PORT}
ENTRYPOINT ["sh","-c","java -Dserver.port=${SERVER_PORT} -jar /app/app.jar"]
