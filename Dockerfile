# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN javac -d . Main.java cache/*.java connector/*.java controller/*.java service/*.java model/*.java

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app /app
ENV PORT=8080
CMD ["java", "-XX:+UseSerialGC", "-Xms64m", "-Xmx256m", "Main"]
