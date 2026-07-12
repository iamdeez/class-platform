# Plan: ci-cd-deploy

> Branch: 005-ci-cd-deploy | Date: 2026-07-12 | Spec: [spec.md](./spec.md)

## 목차

- [사전 검증 (Constitution Gates)](#사전-검증-constitution-gates)
- [기술 컨텍스트](#기술-컨텍스트)
- [사전 영향도 분석 결과](#사전-영향도-분석-결과)
- [핵심 설계](#핵심-설계)
- [인터페이스 계약](#인터페이스-계약)
- [데이터 모델](#데이터-모델)
- [테스트 전략](#테스트-전략)
- [기타 고려사항](#기타-고려사항)

## 사전 검증 (Constitution Gates)

- [x] P-001 도메인 순수성: 이 spec은 `domain` 계층을 포함한 애플리케이션 코드를 변경하지 않는다(설정 파일·Dockerfile·CI 워크플로우만 변경). 위반 없음.
- [x] P-002 성능 원칙: 새로운 비효율(N+1 등)을 도입하지 않는다. Render 무료 티어의 콜드 스타트는 코드 비효율이 아닌 인프라 트레이드오프이며 spec.md 범위 외로 명시했다.
- [x] P-003 호환성 원칙: `application.yml`의 환경변수 오버라이드는 모두 기존 로컬 개발 기본값을 유지하는 방식(`${VAR:기존값}`)으로 설계해 001~004에서 이미 통과한 테스트·로컬 실행 방식을 깨지 않는다.
- [x] P-004 테스트 원칙: 모든 FR(FR-001~006)에 SC(SC-001~006)가 대응한다. 다만 이 spec은 순수 인프라 변경이라 SC 검증이 JUnit 코드 테스트가 아닌 **운영 절차(수동 확인)**로 이루어진다 — "기타 고려사항"에 근거를 명시한다.
- [x] P-005 스펙 범위 원칙: 테스트 커버리지 확대, 모니터링, 커스텀 도메인 등은 spec.md에서 명시적으로 범위 외로 뺐다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **CI**: GitHub Actions (저장소가 이미 GitHub에 있음)
- **컨테이너 빌드**: Docker 멀티스테이지 빌드(`eclipse-temurin:17-jdk-alpine` → `eclipse-temurin:17-jre-alpine`)
- **배포 플랫폼(컴퓨트)**: Render.com 무료 Web Service (Dockerfile 기반)
- **관리형 DB**: MongoDB Atlas M0(MongoDB), Upstash(Redis), Aiven(MySQL) — 모두 무료 티어
- **헬스체크**: Spring Boot Actuator(`/actuator/health`)

## 사전 영향도 분석 결과

`research.md`의 "영향 파일 목록" 표를 그대로 따른다. 요약:

| 파일 | 변경 유형 |
|---|---|
| `build.gradle.kts` | 수정 (actuator 추가) |
| `src/main/resources/application.yml` | 수정 (환경변수 전체 오버라이드, redis 인증/TLS, `server.port`, actuator 노출) |
| `Dockerfile` | 신규 |
| `.dockerignore` | 신규 |
| `.github/workflows/ci-cd.yml` | 신규 |
| `.env.example` | 수정 (배포용 변수 키 문서화) |

## 핵심 설계

### Dockerfile (멀티스테이지)

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`-x test`로 이미지 빌드 시 테스트를 재실행하지 않는다 — 테스트는 CI의 `test` job에서 이미 통과가 검증된 커밋만 이 단계에 도달하므로 중복 실행을 피한다(무료 CI 시간 절약, NFR-001).

### `application.yml` 변경

```yaml
server:
  port: ${PORT:8080}   # Render가 컨테이너에 주입하는 PORT를 우선 사용(12-factor 패턴)

spring:
  datasource:
    url: ${MYSQL_JDBC_URL:jdbc:mysql://localhost:3306/${MYSQL_DATABASE:class_platform}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul}
    username: ${MYSQL_USER:class_platform}
    password: ${MYSQL_PASSWORD:class_platform}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://${MONGO_ROOT_USERNAME:root}:${MONGO_ROOT_PASSWORD:root}@localhost:27017/${MONGO_DATABASE:class_platform}?authSource=admin}
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never   # 배포 정보(DB 연결 상세 등)가 공개 URL에 노출되지 않도록 최소 노출 유지
```

`MYSQL_JDBC_URL`/`MONGODB_URI`를 설정하지 않으면 기존 로컬 개발 값 그대로 동작한다(P-003 호환성).

### CI/CD 워크플로우 (`.github/workflows/ci-cd.yml`)

```yaml
name: CI/CD

on:
  push:
    branches: ['**']
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew test

  deploy:
    needs: test
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Render deploy hook
        run: curl -fsS -X POST "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
```

**`test` → `deploy` 단일 워크플로우 내 `needs` 의존**으로 FR-004(테스트 실패 시 배포 차단)를 보장한다. `deploy` job은 main에 대한 push 이벤트에서만 실행되며(PR에서는 실행 안 함), `test` job이 실패하면 GitHub Actions가 `deploy` job 자체를 스킵한다.

Render 쪽에서는 GitHub 연동의 "Auto-Deploy"를 **꺼야** 한다 — 켜져 있으면 Render가 테스트 결과와 무관하게 push마다 자체적으로 재배포를 시도해 FR-004를 무력화한다. 배포는 오직 `RENDER_DEPLOY_HOOK_URL`(Render가 서비스별로 발급하는 배포 트리거 URL, GitHub Actions secret으로 저장)을 통해서만 일어나도록 구성한다.

## 인터페이스 계약

새 REST 엔드포인트 1개가 추가된다(Actuator 자동 등록, 커스텀 코드 없음):

| Method | Path | 응답 | 관련 SC |
|---|---|---|---|
| GET | `/actuator/health` | `200 {"status":"UP"}` (연결 실패 시 `503 {"status":"DOWN"}`) | SC-005 |

기존 API 계약은 변경하지 않는다(P-003).

## 데이터 모델

해당 없음 — 이 spec은 도메인 데이터 모델을 변경하지 않는다(인프라·설정 전용).

## 테스트 전략

이 spec은 순수 인프라/CI/CD 변경이라, 대부분의 SC가 JUnit 코드 테스트가 아닌 **운영 절차(수동 검증)**로 확인된다 — "기타 고려사항"에서 이 판단의 근거를 설명한다.

| SC 식별자 | 검증 방식 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 수동(GitHub Actions 탭 확인) | 임의 브랜치에 push | `git push` | `test` job이 자동 트리거되어 전체 스위트 실행 |
| SC-002 | 수동(branch protection + 의도적 실패 PR) | 실패하는 테스트를 담은 PR 생성 | 실패 커밋 | GitHub 병합 버튼 비활성화(Required status check 미충족) |
| SC-003 | 수동(배포 후 응답 비교) | main 병합 후 15분 대기 | main 병합 | 신규 커밋의 변경 사항이 배포된 응답에서 확인됨 |
| SC-004 | 수동(강제 병합 재현) | 실패 테스트 포함 커밋을 관리자 권한으로 강제 병합 | 강제 병합 | `deploy` job이 `needs: test` 미충족으로 스킵되거나 실패 종료, 이전 배포 유지 |
| SC-005 | 수동(`curl`) | 배포 완료 후 헬스체크 요청 | `curl {URL}/actuator/health` | `200 {"status":"UP"}` |
| SC-006 | 자동(`git grep`) | 저장소 전체에서 평문 시크릿 패턴 검색 | `git grep -i "password\s*[:=]\s*['\"][^$]"` 등 | 매치 없음(플레이스홀더/환경변수 참조만 존재) |

## 기타 고려사항

- **"테스트"의 성격**: 001~004는 도메인 로직이라 JUnit으로 SC를 자동 검증할 수 있었지만, 이 spec의 SC는 "GitHub UI에 병합 버튼이 비활성화되는가", "실제 배포된 URL이 응답하는가"처럼 외부 플랫폼의 상태를 확인하는 항목이 대부분이라 자동화된 단위/통합 테스트로 포착할 수 없다. SC-006(시크릿 평문 노출 검사)만 `git grep` 기반으로 자동화 가능하다. tasks.md의 Phase 3(검증)은 이 운영 절차를 태스크 단위로 명시한다.
- **AI가 대행할 수 없는 작업**: MongoDB Atlas·Upstash·Aiven·Render 계정 생성 및 서비스 프로비저닝, 각 서비스의 접속 정보를 Render 환경변수로 입력, GitHub 저장소 Secrets/Branch protection 설정은 웹 UI 상의 계정 소유자 조작이 필요해 AI가 수행할 수 없다. tasks.md에서 "사용자 수동 작업"으로 명확히 구분한다.
- **Aiven MySQL TLS**: `sslMode=REQUIRED`로 암호화만 보장하고 서버 인증서 검증(`VERIFY_CA`)은 하지 않는다(research.md 참조) — 포트폴리오 목적의 의도적 단순화.
