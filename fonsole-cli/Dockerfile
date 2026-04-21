FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /app
COPY ./ ./
RUN chmod +x ./gradlew && ./gradlew :fonsole-cli:shadowJar


FROM eclipse-temurin:21-jre-noble AS runner
WORKDIR /app
COPY --from=builder /app/fonsole-cli/build/libs/fonsole-cli-all.jar /app
ENTRYPOINT ["java","-jar", "/app/fonsole-cli-all.jar"]
