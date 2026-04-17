FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 1. Copy only the POM first
COPY pom.xml .

# 2. CACHING STEP: Download dependencies (including the versions plugin)
# This layer will be cached unless pom.xml changes
RUN mvn dependency:go-offline

# 3. Copy source code and build
COPY src ./src
RUN mvn package -DskipTests

# --- Final Stage ---
FROM eclipse-temurin:25.0.2_10-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/cloud-sql-sqlserver-sample-1.0-SNAPSHOT.jar ./app.jar

CMD ["java", "-jar", "app.jar"]
