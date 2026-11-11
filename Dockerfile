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
# Memory and garbage collector for Render Pro (2 CPU, 4 GB). The heap is only part of what Java uses: classes
# (metaspace), thread stacks (100 web threads) and buffers come on top, so the heap gets 70% and the rest has ceilings.
# G1 suits 2+ CPUs and a few GB (short pauses while people wait); on running out of memory exit at once, so Render
# restarts a clean copy instead of a half-working one answering slowly.
# JVM_OPTS replaces these on Render without a rebuild; for a small instance (512 MB, 1 CPU) use:
#   -XX:MaxRAMPercentage=55 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError
# (with TOMCAT_THREADS=40 and DB_POOL_SIZE=10). JAVA_OPTS adds further options after these.
ENV JVM_OPTS="-XX:MaxRAMPercentage=70 -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError"
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS $JAVA_OPTS -jar app.jar"]
