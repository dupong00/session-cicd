# ===== 1단계: 빌드 =====
# JDK가 있는 이미지에서 소스를 jar로 빌드한다. (build 스테이지)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 먼저 gradle 관련 파일만 복사 → 의존성 캐시 레이어를 소스와 분리
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# 소스 복사 후 실행 가능한 jar 생성 (--no-daemon: 컨테이너 안에선 데몬 불필요)
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ===== 2단계: 실행 =====
# JDK 통째로가 아니라 가벼운 JRE 이미지에 jar만 얹어 최종 이미지를 작게 만든다.
FROM eclipse-temurin:25-jre
WORKDIR /app

# 빌드 스테이지에서 만든 jar만 가져온다
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
