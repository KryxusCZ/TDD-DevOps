# ---- Build stage ----
# Pouzijeme plny JDK pro kompilaci
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

# ---- Runtime stage ----
# Pouze JRE — mensi vysledny image (neobsahuje kompilator)
FROM eclipse-temurin:21-jre-alpine

# Non-root user — pozadavek bezpecnostniho best-practice (zadani DevOps)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Zkopirujeme JAR ze stavove faze
COPY --from=build /build/target/room-booking-*.jar app.jar

# Spustime jako non-root
USER appuser

EXPOSE 8080

# HEALTHCHECK: Docker overuje ze aplikace bezi
# --start-period=60s da Spring Bootu cas nastartovat pred prvni kontrolou
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
