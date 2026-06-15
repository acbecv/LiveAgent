FROM openjdk:17-jdk-slim

LABEL maintainer="echomind"
LABEL description="EchoMind AI Customer Service Sidecar"

WORKDIR /app

COPY target/echo-mind-service-*.jar app.jar

EXPOSE 8082

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1
