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
| dev (로컬) | 개발·학습 목적, Docker Compose로 MySQL·MongoDB 컨테이너 기동 | 구현 완료 (001 T002, 002 T001) |
| staging / prod | 없음 | 005 spec(CI/CD·배포)에서 계획 예정. 연습 프로젝트 특성상 실제 운영 환경은 없을 수 있음 |

## 2. 인프라 토폴로지

### 구성 개요

```
[Spring Boot App] ──→ [MySQL 8.0 컨테이너]    (course, enrollment — Flyway 마이그레이션)
                  └──→ [MongoDB 8.0 컨테이너]  (post, comment — 스키마리스)
                  └──→ [Claude API (외부, Anthropic)]  (post의 AI 태깅, 비동기)
```

### 컴포넌트 목록

| 컴포넌트 | 유형 | 역할 | 비고 |
|---|---|---|---|
| `class-platform-mysql` | Docker 컨테이너 (`mysql:8.0`) | course/enrollment 영속성 | `docker-compose.yml`. 포트 3306, healthcheck(`mysqladmin ping`) |
| `class-platform-mongodb` | Docker 컨테이너 (`mongo:8.0`) | post/comment 영속성 | `docker-compose.yml`. 포트 27017, healthcheck(`mongosh ping`) |
| Claude API | 외부 관리형 서비스 (Anthropic) | AI 태깅(카테고리 태그·요약) | `anthropic-java` SDK, `AnthropicOkHttpClient.fromEnv()`로 OS 환경변수(`ANTHROPIC_API_KEY`)에서 직접 자격증명을 읽는다(`application.yml` 미경유) |

003 spec에서 Redis(캐싱)가 추가될 예정이다.

## 3. 배포 방식

[미파악] — 005 spec(테스트 강화·CI/CD·배포 자동화)에서 GitHub Actions 기반 CI/CD, AWS 배포를 계획 중이나 아직 미착수.

## 4. 모니터링·로깅

[미파악] — 채용공고에서 Pinpoint/Sentry가 언급되나, 이 연습 프로젝트에 도입할지는 미결정. 도입 시 별도 spec으로 설계한다.

## 5. 연결 실패 재시도 동작

| 대상 | 재시도 방식 | 간격 | 동작 영향 |
|---|---|---|---|
| Claude API | `anthropic-java` SDK 기본 재시도(429/5xx 대상, 기본 2회) | SDK 기본값 | `EnrichPostUseCase`가 `AiTaggingFailedException`을 흡수해 `Post.aiStatus=FAILED`로 전이시키므로, 재시도 소진 후에도 게시글 등록 자체는 영향받지 않는다(NFR-004) |
| MySQL/MongoDB | 별도 재시도 로직 없음(Spring Boot 기본 커넥션 풀 동작에 위임) | — | 로컬 개발 환경 한정이라 별도 설계하지 않음 |

## 6. 로컬 개발 환경

### 의존성 설치

Gradle Wrapper가 의존성을 자동 관리한다 (`./gradlew`).

### 실행

```bash
docker compose up -d   # MySQL + MongoDB 컨테이너 기동
./gradlew bootRun
```

`ANTHROPIC_API_KEY`를 셸에 export하지 않아도 애플리케이션은 정상 기동한다(빈 생성 시점에 자격증명을 검증하지 않음). 다만 이 경우 AI 태깅 호출 시점에 실패해 `Post.aiStatus=FAILED`로 남는다.

### 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers로 MySQL·MongoDB를 자동 기동한다(로컬 `docker-compose.yml`과 별개, Docker 데몬만 필요).

### 의존성 구조

| 패키지 | 역할 | 프로파일 |
|---|---|---|
| `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-mysql`, `mysql-connector-j` | course/enrollment 영속성 | 공통 (001) |
| `spring-boot-starter-data-mongodb` | post/comment 영속성 | 공통 (002) |
| `com.anthropic:anthropic-java` | Claude API 클라이언트 | 공통 (002) |
| `org.jsoup:jsoup` | HTML sanitize (XSS 방어) | 공통 (001 후속) |
| `org.testcontainers:{mysql,mongodb,junit-jupiter}` | 통합 테스트 | test 전용 |

## 7. 배포 전 확인 체크리스트

- [ ] (아직 배포 대상 아님 — 005 spec에서 정의 예정)

## 8. 알려진 인프라 제약

| 항목 | 내용 | 영향 범위 | 관련 spec |
|---|---|---|---|
| CI/CD·배포 파이프라인 부재 | GitHub Actions, AWS 배포 등이 아직 없음 | 배포 전체 | 005 spec(미착수) |
| Redis 미도입 | 캐싱 계층이 아직 없음 | 인프라 | 003 spec(설계 중) |
| Claude API 실제 자격증명 필요 시점 | `ANTHROPIC_API_KEY` 없이도 앱은 기동하지만, 실제 AI 태깅 호출 시점에만 실패가 드러난다(빈 생성 시점 검증 없음) | `post` 도메인 AI 태깅 | `docs/specs/v0.1.0/002-community-post-ai-tagging/tasks.md` T003 |

## 9. 갱신 이력

| 날짜 | commit | 갱신 내용 | 관련 spec |
|---|---|---|---|
| 2026-07-09 | (커밋 전) | 최초 작성 (인프라 구성 파일 없음) | — |
| 2026-07-12 | `d8727d0` | 001(MySQL 컨테이너)·002(MongoDB 컨테이너, Claude API 외부 연동) 완료분을 반영해 전면 갱신. 001/002 구현 중 인프라가 실제로 변경됐음에도 이 문서가 갱신되지 않아 낡은 상태(파일 부재로 기술)로 남아있던 것을 003 설계 착수 전에 바로잡음 | `docs/specs/v0.1.0/001-class-enrollment-core/`, `docs/specs/v0.1.0/002-community-post-ai-tagging/` |
