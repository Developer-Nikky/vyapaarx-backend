FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

# Compile with proper classpath
RUN javac -d . $(find . -name "*.java")

CMD ["sh", "-c", "java Main"]
