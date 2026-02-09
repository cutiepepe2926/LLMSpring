FROM eclipse-temurin:17-jdk

LABEL creator="원하는 내용"

ARG jarfile=build/libs/LlmSpring-0.0.1-SNAPSHOT.jar

COPY ${jarfile} /app.jar

CMD ["java", "-jar", "/app.jar"]