FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

RUN javac *.java

CMD ["sh", "-c", "java Main"]
