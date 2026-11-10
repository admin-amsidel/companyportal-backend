# ---- Build stage: compiles the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# .mvn/ is not copied, so the build uses Maven Central (no corporate mirror)
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- Run stage: small image with just the JRE + jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Memory for a small (512 MB) cloud instance. The heap is only part of what Java uses: classes (metaspace), thread
# stacks and buffers come on top, and at 75% heap they went over 512 MB under load, so the instance was killed (502s
# until Render restarted it). So: heap 55%, ceilings on metaspace and compiled code, smaller thread stacks, the serial collector
# (least memory on 1 CPU), and on running out of memory exit at once, so Render restarts a clean copy instead of a
# half-working one answering slowly. JAVA_OPTS adds to or overrides these (e.g. -XX:MaxRAMPercentage=70 on 2 GB).
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=55 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError $JAVA_OPTS -jar app.jar"]
