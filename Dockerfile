FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV WEB_ONLY=true
ENV DB_DATA_DIR=/app/data
COPY --from=build /app/target/controle-estoque-1.0.0.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
