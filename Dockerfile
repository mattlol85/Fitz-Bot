# Stage 1: Compile the application using Gradle
FROM gradle:jdk11 as builder
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew clean build

# Stage 2: Set up the production environment with OpenJDK
FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/*.jar /app/Fitz-Bot.jar
ENV DISCORD_TOKEN=placeholder_token
ENTRYPOINT ["java", "-jar", "/app/Fitz-Bot.jar", "$DISCORD_TOKEN"]
