# Stage 1: Build the application
# We use a maven image to compile the code so you don't need Maven installed locally
FROM maven:3.9.6-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the jar
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/naftiko-capability-0.2-SNAPSHOT.jar app.jar

# Copy a default naftiko.yaml into the image (Optional fallback)
COPY naftiko.yaml /app/naftiko.yaml

# Set the entrypoint to run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
