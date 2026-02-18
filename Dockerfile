# ---- 1단계: Build (Gradle + JDK) ----
FROM gradle:8.6-jdk17 AS builder

WORKDIR /app
COPY . .

# 캐시 활용을 위해 Gradle 캐시 폴더 유지 (선택)
# RUN gradle dependencies --no-daemon

# 테스트를 건너뛰고 JAR 빌드
RUN gradle clean bootJar -x test --no-daemon

# ---- 2단계: Runtime (JRE Only) ----
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 jar 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 설정 (Spring Boot 기본 8080)
EXPOSE 8080

# 환경변수로 프로파일 주입
# docker-compose.yml의 SPRING_PROFILES_ACTIVE 값이 여기에 전달됨
ENV SPRING_PROFILES_ACTIVE=prod

# JVM 옵션 (필요 시 compose에서 JAVA_OPTS로 오버라이드 가능)
ENV JAVA_OPTS=""

# 실행 명령
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
