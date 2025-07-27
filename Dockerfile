FROM gradle:jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon --stacktrace --info --console=plain --refresh-dependencies -x test

FROM eclipse-temurin:21-jre-alpine
ARG APP_DIR=app
WORKDIR /$APP_DIR
COPY --from=build /app/build/libs/*.jar authentication.jar
ENV PROFILE_MODE=prod
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.active=$PROFILE_MODE -jar authentication.jar"]