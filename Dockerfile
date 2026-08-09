# ===== Build stage =====
FROM gradle:8.10-jdk17-alpine AS build
WORKDIR /app

# 빌드 스크립트 먼저 복사 → 의존성 레이어 캐싱 (소스만 바뀔 땐 재다운로드 안 함)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능한 jar 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# plain jar를 껐으므로 build/libs 에는 실행 가능한 jar 하나만 존재
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

RUN apk add --no-cache tzdata
ENV TZ=UTC

EXPOSE 8080

# 컨테이너에 할당된 메모리에 맞춰 힙 자동 조정
ENTRYPOINT ["java", "-jar", "app.jar"]
