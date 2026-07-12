# Project Infra

> 이 문서는 프로젝트의 **운영 수준 인프라 지식**을 기록하는 참조 문서다.
> 배포·환경 구성에 영향을 주는 spec 설계 전 반드시 읽어 운영 제약을 파악한다.
>
> - **갱신 시점**: 인프라 구성이 변경된 spec 완료 후 갱신한다.
> - **환경변수**: `.env` / `.env.example` 파일로 관리한다. `.env.example`이 기준 문서다.
> - **기준 커밋**: 이 문서는 **§9 갱신 이력의 마지막 commit 기준**이다.

---

## 목차

- [1. 환경 구성](#1-환경-구성)
- [2. 인프라 토폴로지](#2-인프라-토폴로지)
- [3. 배포 방식](#3-배포-방식)
- [4. 모니터링·로깅](#4-모니터링로깅)
- [5. 연결 실패 재시도 동작](#5-연결-실패-재시도-동작)
- [6. 로컬 개발 환경](#6-로컬-개발-환경)
- [7. 배포 전 확인 체크리스트](#7-배포-전-확인-체크리스트)
- [8. 알려진 인프라 제약](#8-알려진-인프라-제약)
- [9. 갱신 이력](#9-갱신-이력)

---

## 1. 환경 구성

| 환경 | 목적 | 비고 |
|---|---|---|
| dev (로컬) | 개발·학습 목적, Docker Compose로 MySQL·MongoDB·Redis 컨테이너 기동 | 구현 완료 (001 T002, 002 T001, 003 T001) |
| prod | 포트폴리오 데모용 공개 배포 | 구현 완료 (005). Render 무료 Web Service + MongoDB Atlas M0 + Upstash Redis + Aiven MySQL. 별도 staging 환경은 없다(연습 프로젝트 특성상 dev→prod 직행) |

## 2. 인프라 토폴로지

### 구성 개요

```
[dev — 로컬]
[Spring Boot App] ──→ [MySQL 8.0 컨테이너]    (course, enrollment — Flyway 마이그레이션.
                  │                             JPA(course/enrollment)와 MyBatis(statistics)가
                  │                             동일 DataSource를 공유)
                  └──→ [MongoDB 8.0 컨테이너]  (post, comment — 스키마리스)
                  └──→ [Redis 7(alpine) 컨테이너]  (post의 좋아요·조회수·인기 랭킹·상세 캐시)
                  └──→ [Claude API (외부, Anthropic)]  (post의 AI 태깅, 비동기)

[prod — 배포 (005)]
[GitHub Actions] ──test 통과──→ [Render Deploy Hook] ──→ [Render Web Service (Docker)]
                                                              │
                                                              ├──→ [Aiven MySQL 무료 티어]
                                                              ├──→ [MongoDB Atlas M0]
                                                              ├──→ [Upstash Redis 무료 티어]
                                                              └──→ [Claude API (외부, Anthropic)]
```

MyBatis(`statistics`)는 별도 컨테이너·연결 설정이 없다 — `course`/`enrollment`(JPA)와 동일한 MySQL `DataSource`를 공유하며, Spring Boot가 단일 `PlatformTransactionManager`로 두 영속성 기술의 트랜잭션 참여를 함께 관리한다(004).

### 컴포넌트 목록

| 컴포넌트 | 유형 | 역할 | 프로파일 | 비고 |
|---|---|---|---|---|
| `class-platform-mysql` | Docker 컨테이너 (`mysql:8.0`) | course/enrollment 영속성 | dev | `docker-compose.yml`. 포트 3306, healthcheck(`mysqladmin ping`) |
| `class-platform-mongodb` | Docker 컨테이너 (`mongo:8.0`) | post/comment 영속성 | dev | `docker-compose.yml`. 포트 27017, healthcheck(`mongosh ping`) |
| `class-platform-redis` | Docker 컨테이너 (`redis:7-alpine`) | post의 좋아요(Set)·조회수(카운터)·인기 랭킹(ZSet)·상세 캐시(cache-aside) | dev | `docker-compose.yml`. 포트 6379, healthcheck(`redis-cli ping`). 인증 없음(로컬 전용) |
| Claude API | 외부 관리형 서비스 (Anthropic) | AI 태깅(카테고리 태그·요약) | 공통 | `anthropic-java` SDK, `AnthropicOkHttpClient.fromEnv()`로 OS 환경변수(`ANTHROPIC_API_KEY`)에서 직접 자격증명을 읽는다(`application.yml` 미경유) |
| Render Web Service | 외부 관리형 컴퓨트 (무료) | 애플리케이션 컨테이너 호스팅 | prod | Dockerfile 기반, `https://class-platform-quan.onrender.com`. Auto-Deploy Off, Deploy Hook(GitHub Actions secret)으로만 배포 트리거. 15분 유휴 시 슬립(콜드 스타트 30~60초) |
| Aiven MySQL | 외부 관리형 DB (무료 티어) | course/enrollment/statistics 영속성 | prod | `sslMode=REQUIRED`(서버 인증서 검증은 생략, 포트폴리오 목적 단순화) |
| MongoDB Atlas M0 | 외부 관리형 DB (무료, 영구) | post/comment 영속성 | prod | `mongodb+srv://` 연결, Network Access `0.0.0.0/0`(Render가 고정 IP를 제공하지 않아 불가피) |
| Upstash Redis | 외부 관리형 캐시 (무료 티어) | post의 좋아요·조회수·인기 랭킹·상세 캐시 | prod | TLS 필수. "REST API" 토큰이 아닌 "Redis"(TCP) 비밀번호 사용 |

## 3. 배포 방식

### CI/CD 파이프라인

`.github/workflows/ci-cd.yml` (GitHub Actions), 2-job 구조:

```
push(모든 브랜치) 또는 PR(→main)
      ↓
test job — ./gradlew test (Testcontainers로 MySQL/MongoDB 자동 기동)
      ↓ (needs: test, main push에서만)
deploy job — curl -X POST {RENDER_DEPLOY_HOOK_URL}
      ↓
Render가 Dockerfile 기반으로 재빌드·재배포
```

`test` job 실패 시 `deploy` job은 GitHub Actions가 자동으로 skip한다(FR-004). main 브랜치는 "Require status checks to pass before merging"(`test` 필수) 보호 규칙이 걸려 있으나, 저장소 소유자의 직접 push는 `enforce_admins: false` 설정으로 이 규칙을 우회할 수 있다 — 다만 `deploy` job의 `needs: test` 게이트는 push 경로와 무관하게 항상 적용되므로 실제 배포 안전성에는 영향이 없다.

### 빌드

```bash
docker build -t class-platform .
```

Render 배포 대상이 x86_64이므로 `Dockerfile`의 두 스테이지 모두 `--platform=linux/amd64`로 고정되어 있다(로컬 arm64 Mac에서도 에뮬레이션으로 빌드됨).

### 배포 절차

```
① 코드 push/PR → GitHub Actions test job 실행
      ↓
② main 병합(test 통과 필수) → push 이벤트로 deploy job 실행
      ↓
③ deploy job이 Render Deploy Hook 호출
      ↓
④ Render가 GitHub에서 최신 main을 pull → Dockerfile 빌드 → 무중단 교체 배포
```

### 롤백 방법

Render 대시보드의 "Deploys" 탭에서 이전 성공 배포를 선택해 "Redeploy"하거나, `git revert`로 문제 커밋을 되돌린 뒤 main에 병합해 재배포한다(T016에서 이 흐름을 실제로 검증함).

## 4. 모니터링·로깅

[미파악] — 채용공고에서 Pinpoint/Sentry가 언급되나, 이 연습 프로젝트에 도입할지는 미결정. 도입 시 별도 spec으로 설계한다.

## 5. 연결 실패 재시도 동작

| 대상 | 재시도 방식 | 간격 | 동작 영향 |
|---|---|---|---|
| Claude API | `anthropic-java` SDK 기본 재시도(429/5xx 대상, 기본 2회) | SDK 기본값 | `EnrichPostUseCase`가 `AiTaggingFailedException`을 흡수해 `Post.aiStatus=FAILED`로 전이시키므로, 재시도 소진 후에도 게시글 등록 자체는 영향받지 않는다(NFR-004) |
| Redis | 별도 재시도 로직 없음(Lettuce 기본 동작에 위임) | — | `PostPopularityPort`/`PostCachePort` 호출부(`GetPostUseCase` 등)가 `runCatching`으로 예외를 흡수해 Redis 장애 시에도 게시글 조회는 저장된 스냅샷 값으로 정상 응답한다(NFR-002) |
| MySQL/MongoDB | 별도 재시도 로직 없음(Spring Boot 기본 커넥션 풀 동작에 위임) | — | dev/prod 공통. prod(Aiven/Atlas)는 관리형 서비스라 dev보다 가용성이 높지만 별도 재시도 로직을 추가하지는 않았다 |
| Render 배포(Deploy Hook) | 재시도 없음(1회 호출) | — | `curl -fsS`로 실패 시 `deploy` job 자체가 실패로 종료된다. 재배포가 필요하면 GitHub Actions에서 워크플로우를 재실행(`gh run rerun` 또는 UI)하거나 Render 대시보드에서 수동 재배포 |

## 6. 로컬 개발 환경

### 의존성 설치

Gradle Wrapper가 의존성을 자동 관리한다 (`./gradlew`).

### 실행

```bash
docker compose up -d   # MySQL + MongoDB + Redis 컨테이너 기동
./gradlew bootRun
```

`ANTHROPIC_API_KEY`를 셸에 export하지 않아도 애플리케이션은 정상 기동한다(빈 생성 시점에 자격증명을 검증하지 않음). 다만 이 경우 AI 태깅 호출 시점에 실패해 `Post.aiStatus=FAILED`로 남는다.

### 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers로 MySQL·MongoDB·Redis를 자동 기동한다(로컬 `docker-compose.yml`과 별개, Docker 데몬만 필요). Redis는 공식 Testcontainers 모듈이 없어 `GenericContainer("redis:7-alpine")`을 사용한다(`mysql`/`mongodb` Testcontainers 모듈이 core를 전이 의존성으로 가져와 별도 추가 불필요). GitHub Actions `ubuntu-latest` 러너에는 Docker가 사전 설치되어 있어 Testcontainers가 별도 설정 없이 동작한다(005).

### Docker 이미지 로컬 빌드/실행 검증

```bash
docker build -t class-platform .
docker run --rm -p 8080:8080 \
  -e MYSQL_JDBC_URL="jdbc:mysql://host.docker.internal:3306/class_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul" \
  -e MONGODB_URI="mongodb://root:root@host.docker.internal:27017/class_platform?authSource=admin" \
  -e REDIS_HOST=host.docker.internal \
  class-platform
```

로컬 `docker-compose` 컨테이너에 연결하려면 컨테이너 안에서 `localhost`가 아닌 `host.docker.internal`로 접근해야 한다(Mac Docker Desktop 기준).

### 의존성 구조

| 패키지 | 역할 | 프로파일 |
|---|---|---|
| `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-mysql`, `mysql-connector-j` | course/enrollment 영속성 | 공통 (001) |
| `spring-boot-starter-data-mongodb` | post/comment 영속성 | 공통 (002) |
| `com.anthropic:anthropic-java` | Claude API 클라이언트 | 공통 (002) |
| `org.jsoup:jsoup` | HTML sanitize (XSS 방어) | 공통 (001 후속) |
| `spring-boot-starter-data-redis` | 좋아요·조회수·인기 랭킹·상세 캐시 | 공통 (003) |
| `mybatis-spring-boot-starter` | 강사 대시보드 통계 집계(GROUP BY 복잡 쿼리) | 공통 (004) |
| `spring-boot-starter-actuator` | 헬스체크(`/actuator/health`) | 공통 (005) |
| `org.testcontainers:{mysql,mongodb,junit-jupiter}` | 통합 테스트 | test 전용 |

## 7. 배포 전 확인 체크리스트

- [x] 로컬 `./gradlew test` 전체 통과 확인
- [x] `docker build`로 이미지 빌드 성공 확인(로컬 `docker-compose` 대상으로 `docker run` 기동 확인)
- [x] Render Environment Variables에 `MYSQL_JDBC_URL`/`MYSQL_USER`/`MYSQL_PASSWORD`/`MONGODB_URI`/`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`/`REDIS_SSL_ENABLED`/`ANTHROPIC_API_KEY` 설정 확인
- [x] Render Auto-Deploy Off 확인(Deploy Hook으로만 배포 트리거)
- [x] GitHub Actions Secret `RENDER_DEPLOY_HOOK_URL` 등록 확인
- [x] main 브랜치 보호 규칙(`test` 필수 체크) 등록 확인
- [x] 배포된 공개 URL의 `/actuator/health`가 200을 반환하는지 확인

## 8. 알려진 인프라 제약

| 항목 | 내용 | 영향 범위 | 관련 spec |
|---|---|---|---|
| Claude API 실제 자격증명 필요 시점 | `ANTHROPIC_API_KEY` 없이도 앱은 기동하지만, 실제 AI 태깅 호출 시점에만 실패가 드러난다(빈 생성 시점 검증 없음) | `post` 도메인 AI 태깅 | `docs/specs/v0.1.0/002-community-post-ai-tagging/tasks.md` T003 |
| Redis 인증·영속화 미설정(dev 한정) | 로컬 `redis:7-alpine` 컨테이너는 비밀번호 없이 기동하며 AOF/RDB 영속화 설정도 기본값(변경 없음)이다. 컨테이너 재시작 시 아직 MongoDB에 동기화되지 않은 좋아요·조회수 증분이 유실될 수 있다. prod(Upstash)는 인증·TLS가 기본 적용됨 | 인프라 전체(로컬 한정) | `docs/specs/v0.1.0/003-like-view-count-caching/` |
| Render 무료 티어 콜드 스타트 | 15분 유휴 시 슬립, 재요청 시 30~60초 지연. 배포 직후 엣지 라우팅이 간헐적으로 `no-server` 404를 반환하는 현상도 관찰됨(재요청 시 정상) | 배포 환경(prod) | `docs/specs/v0.1.0/005-ci-cd-deploy/` |
| 브랜치 보호가 관리자 직접 push를 막지 못함 | `enforce_admins: false`라 저장소 소유자는 main에 직접 push해 브랜치 보호(Required status check)를 우회할 수 있다. 다만 `deploy` job의 `needs: test` 게이트는 이와 무관하게 항상 적용되어 실제 배포 안전성에는 영향 없음(T016 검증) | 배포 환경(prod) | `docs/specs/v0.1.0/005-ci-cd-deploy/` |
| IaC 미도입 | Render/Atlas/Upstash/Aiven 리소스를 모두 웹 UI로 수동 프로비저닝했다. Terraform(mongodbatlas/upstash/aiven 공식 provider) + Render `render.yaml` Blueprint 조합이 다음 후보(006 spec 논의 중) | 배포 환경(prod) | 006 spec(미착수) |
| 저장소가 public | 브랜치 보호 규칙이 private 저장소에서 GitHub Pro를 요구해 005에서 public으로 전환했다. 소스 코드가 공개되므로 향후 민감한 설계 문서·주석을 추가할 때 이 점을 고려해야 한다 | 저장소 전체 | `docs/specs/v0.1.0/005-ci-cd-deploy/` |

## 9. 갱신 이력

| 날짜 | commit | 갱신 내용 | 관련 spec |
|---|---|---|---|
| 2026-07-09 | (커밋 전) | 최초 작성 (인프라 구성 파일 없음) | — |
| 2026-07-12 | `d8727d0` | 001(MySQL 컨테이너)·002(MongoDB 컨테이너, Claude API 외부 연동) 완료분을 반영해 전면 갱신. 001/002 구현 중 인프라가 실제로 변경됐음에도 이 문서가 갱신되지 않아 낡은 상태(파일 부재로 기술)로 남아있던 것을 003 설계 착수 전에 바로잡음 | `docs/specs/v0.1.0/001-class-enrollment-core/`, `docs/specs/v0.1.0/002-community-post-ai-tagging/` |
| 2026-07-12 | `1f36131`(+Phase 4 미커밋분) | 003(Redis 컨테이너, 좋아요·조회수·인기 랭킹·상세 캐시) 완료분 반영. 인프라 토폴로지·컴포넌트 목록·연결 실패 재시도 동작·로컬 개발 환경·의존성 구조·알려진 제약(Redis 인증·영속화 미설정)을 실제 코드 기준으로 갱신 | `docs/specs/v0.1.0/003-like-view-count-caching/` |
| 2026-07-12 | `2990580` | 004(MyBatis 도입) 완료분 반영. 별도 컨테이너 없이 기존 MySQL `DataSource`를 JPA와 공유함을 인프라 토폴로지에 명시, 의존성 구조에 `mybatis-spring-boot-starter` 추가, "MyBatis 미도입" 제약 항목 해소로 제거 | `docs/specs/v0.1.0/004-complex-query-statistics/` |
| 2026-07-13 | `c63bafe` | 005(GitHub Actions CI/CD + Render/Atlas/Upstash/Aiven 배포) 완료분 반영. prod 환경 신설, CI/CD 파이프라인·배포 절차·롤백 방법 최초 작성("배포 방식" [미파악] 해소), 배포 전 체크리스트 채움, "CI/CD·배포 파이프라인 부재" 제약 해소로 제거하고 콜드 스타트·관리자 push 우회·IaC 미도입·저장소 public 전환 등 새 제약 추가 | `docs/specs/v0.1.0/005-ci-cd-deploy/` |
