FROM eclipse-temurin:17

RUN mkdir /opt/app
COPY out/artifacts/discord_ladder_java_jar/discord-ladder-java.jar /opt/app
CMD ["java", "-jar", "/opt/app/discord-ladder-java.jar"]