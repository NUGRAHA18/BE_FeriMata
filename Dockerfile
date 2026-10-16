FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is reused whenever only source files change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# Runtime stage 
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Never run the application as root.
RUN groupadd --system smartmelon && useradd --system --gid smartmelon --home /app smartmelon

COPY --from=build /workspace/target/*.jar /app/application.jar
RUN chown -R smartmelon:smartmelon /app
USER smartmelon

EXPOSE 8080

# Container memory limits, not a fixed heap size, decide how much the JVM may use.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
