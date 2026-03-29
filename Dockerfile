FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY Main.java .

RUN javac Main.java

CMD ["sh", "-c", "java Main"]
