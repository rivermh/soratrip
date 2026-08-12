# 1단계: 빌드 (Gradle 캐시를 살리려고 의존성 파일을 소스보다 먼저 복사)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행 (JDK 대신 JRE만 담아 이미지 용량을 줄인다)
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
