FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

RUN find . -name "*.java" > sources.txt && javac @sources.txt

CMD ["sh", "-c", "java Main"]
