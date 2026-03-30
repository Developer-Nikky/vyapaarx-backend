# Step 1: Use a lightweight JDK
FROM eclipse-temurin:17-jdk-alpine

# Step 2: Set working directory
WORKDIR /app

# Step 3: Copy all files
COPY . .

# Step 4: Compile all Java files including packages
RUN javac -d . Main.java cache/*.java connector/*.java controller/*.java service/*.java

# Step 5: Start the server with optimized RAM settings
# -Xmx300m ensures Java stays within 300MB, leaving room for OS
CMD ["java", "-Xmx300m", "-Xms150m", "Main"]
