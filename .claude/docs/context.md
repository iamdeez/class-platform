# Project Context

> 이 문서는 프로젝트의 **현재 상태를 묘사**하는 살아있는 참조 문서다.
> 새로운 spec 설계 전 반드시 읽어 프로젝트 구조·흐름·용어를 숙지한다.
>
> - **갱신 시점**: spec 구현·검증 완료 후 갱신한다.
> - **작성 원칙**: 현재 코드베이스의 사실만 기록한다. 미래 계획이나 설계 의도는 spec.md에 작성한다.
> - **기준 커밋**: 이 문서는 **§7 갱신 이력의 마지막 commit 기준**이다.

---

## 목차

- [1. 프로젝트 개요](#1-프로젝트-개요)
- [2. 프로젝트 구조](#2-프로젝트-구조)
- [3. 이벤트 및 데이터 흐름](#3-이벤트-및-데이터-흐름)
- [4. 도메인 모델](#4-도메인-모델)
- [5. 도메인 용어 사전](#5-도메인-용어-사전)
- [6. 알려진 제약 및 기술 부채](#6-알려진-제약-및-기술-부채)
- [7. 갱신 이력](#7-갱신-이력)

---

## 1. 프로젝트 개요

- **프로젝트명**: class-platform
- **목적**: 월부닷컴 Sr. Software Engineer(BE) 채용공고 대비 포트폴리오 연습 — 온라인 클래스 플랫폼(강의/커뮤니티) 미니 클론
- **현재 버전**: v0.1.0 (001 spec 구현 진행 중, tasks.md 기준 13/23 완료)
- **주요 기술 스택 (적용됨)**: Kotlin 1.9.25, Spring Boot 3.3.4, JDK 17, Gradle(Kotlin DSL), Spring Data JPA + Hibernate, Flyway, MySQL 8.0. JUnit5 + MockK/springmockk(유닛), Testcontainers-MySQL(통합). MyBatis·MongoDB·Redis는 아직 미도입 (003/004 spec에서 캐싱·복잡 조회 도입 시 추가 예정). 상세 선정 근거는 `docs/specs/v0.1.0/001-class-enrollment-core/research.md` 참조.

## 2. 프로젝트 구조

### 디렉토리 레이아웃

```
class-platform/
├── CLAUDE.md
├── .claude/docs/                     ← constitution.md, context.md(본 문서), infra.md
├── docs/specs/v0.1.0/                ← 001, 002 spec 설계 문서
├── docker-compose.yml                ← 로컬 MySQL 컨테이너
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/classplatform/
    │   ├── common/                   ← 전 도메인 공유 (UserId, ApiResponse, 예외 처리)
    │   ├── course/                   ← course 애그리거트 (아래 레이어 구조 참조)
    │   └── enrollment/                ← enrollment 애그리거트
    └── main/resources/
        ├── application.yml
        └── db/migration/             ← Flyway 마이그레이션 (V1, V2)
```

### 레이어 구조

`course`, `enrollment` 각 bounded context 패키지 내부는 아래 4개 계층으로 구성된다 (DDD + 포트-어댑터 패턴). `domain`은 프레임워크 비의존 순수 Kotlin이다 (constitution P-001).

```
presentation   ← REST Controller + DTO. X-User-Id 헤더로 사용자 식별
      ↓
application    ← UseCase. 트랜잭션 경계, 도메인 오케스트레이션
      ↓
domain         ← 엔티티(불변식·상태 전이 규칙), Repository 인터페이스(포트), 도메인 예외
      ↑
infrastructure ← JPA Entity + JpaRepository + RepositoryImpl(어댑터). 도메인 ↔ JPA 엔티티 상호 변환
```

### 핵심 모듈 목록

| 모듈 / 클래스 | 위치 | 역할 | 비고 |
|---|---|---|---|
| `Course` | `course/domain/Course.kt` | 강의 애그리거트 루트. `private constructor` + 정적 팩토리(`register`/`reconstitute`)로 생성 제한, `publish()`/`close()`로만 상태 전이 | concrete |
| `CourseStatus` | `course/domain/CourseStatus.kt` | DRAFT→PUBLISHED→CLOSED 단방향 전이 규칙(`canTransitionTo`) | enum |
| `CourseRepository` | `course/domain/CourseRepository.kt` | 포트 인터페이스 | interface |
| `CourseRepositoryImpl` | `course/infrastructure/CourseRepositoryImpl.kt` | JPA 어댑터, `Course` ↔ `CourseJpaEntity` 변환 | `@Repository` |
| `CourseController` | `course/presentation/CourseController.kt` | `/api/courses` REST API (등록/목록/상세/상태변경) | 구현 완료 |
| `Enrollment` | `enrollment/domain/Enrollment.kt` | 수강신청 애그리거트 루트. `cancel()`로만 상태 전이 | concrete |
| `EnrollmentRepository` | `enrollment/domain/EnrollmentRepository.kt` | 포트 인터페이스 | interface |
| `EnrollmentRepositoryImpl` | `enrollment/infrastructure/EnrollmentRepositoryImpl.kt` | JPA 어댑터 | `@Repository` |
| `EnrollUseCase` 등 | `enrollment/application/` | 수강신청/취소/내 신청목록 유스케이스 구현 완료 | `@Service` |
| `EnrollmentController` | (미구현) | enrollment presentation 계층 | **T012 미완료** — §6 참조 |
| `UserId` | `common/UserId.kt` | `@JvmInline value class`, 양수 검증 | 전 도메인 공유 |
| `ApiResponse<T>` | `common/ApiResponse.kt` | 공통 응답 포맷(`data`/`error`) | — |
| `GlobalExceptionHandler` | `common/GlobalExceptionHandler.kt` | 도메인 예외 → HTTP 상태 코드 매핑 (`@RestControllerAdvice`) | — |

## 3. 이벤트 및 데이터 흐름

### 주요 처리 흐름

```
HTTP 요청
      ↓
Controller  — X-User-Id 헤더 파싱, DTO 검증(@Valid)
      ↓
UseCase     — 트랜잭션 경계, 도메인 규칙 오케스트레이션 (예: EnrollUseCase는 CourseRepository로 강의 상태를 먼저 확인)
      ↓
도메인 객체  — 불변식·상태 전이 검증, 위반 시 도메인 예외 throw
      ↓
Repository(포트) → RepositoryImpl(어댑터) → JpaRepository → MySQL
      ↓
GlobalExceptionHandler — 도메인 예외를 HTTP 상태 코드로 변환 (404/409/400/403)
```

동시성 정합성(NFR-002)은 애플리케이션 레벨 락이 아니라 `enrollment` 테이블의 `(course_id, user_id)` **DB 유니크 제약**으로 보장한다.

### 외부 시스템 연동

없음. 001 spec 범위에서는 외부 시스템 연동이 없다 (인증 서버, AI 서비스 등은 002 spec 이후).

## 4. 도메인 모델

### 핵심 엔티티 (구현 완료 기준)

| 엔티티 | 설명 | 주요 속성 | 불변식 |
|---|---|---|---|
| `Course` | 강의 애그리거트 루트 | id, title, description, price(BigDecimal), instructorId(UserId), status(DRAFT/PUBLISHED/CLOSED) | title 공백 불가, price 음수 불가, 상태는 DRAFT→PUBLISHED→CLOSED 단방향만 |
| `Enrollment` | 수강신청 애그리거트 루트 | id, courseId, userId(UserId), status(ACTIVE/CANCELLED) | 이미 CANCELLED인 신청을 재취소하면 예외 |

### 엔티티 관계

```
Course (1) ──── 수강 대상 ───→ (N) Enrollment
```

`Enrollment`는 `courseId`로만 `Course`를 참조한다 (JPA 연관관계 매핑이 아닌 ID 참조 — 애그리거트 간 느슨한 결합 유지).

## 5. 도메인 용어 사전 (Glossary)

| 용어 | 정의 | 사용 금지 동의어 |
|---|---|---|
| Course | 강의. 애그리거트 루트 엔티티명 | Class(Kotlin 예약어 `class`와 혼동 방지 위해 미사용), Lecture |
| Enrollment | 수강신청. 애그리거트 루트 엔티티명 | Registration, Subscription |
| CourseStatus | 강의 상태(DRAFT/PUBLISHED/CLOSED) | — |
| EnrollmentStatus | 수강신청 상태(ACTIVE/CANCELLED) | — |
| UserId | 사용자 식별자 값 객체. `common` 패키지 소속 (모든 bounded context 공유) | — |

## 6. 알려진 제약 및 기술 부채

| 항목 | 내용 | 영향 범위 | 관련 spec |
|---|---|---|---|
| 취소 후 재신청 미지원 | `enrollment` 테이블의 `(course_id, user_id)` 유니크 제약이 CANCELLED 레코드에도 적용되어, 취소한 강의에 동일 사용자가 재신청 불가 (MySQL이 조건부 유니크 인덱스를 지원하지 않아 발생한 설계 트레이드오프) | `enrollment` 도메인 | `docs/specs/v0.1.0/001-class-enrollment-core/research.md` |
| 인증·인가 미구현 | 사용자 식별은 `X-User-Id` 헤더를 신뢰하는 방식으로만 처리되며 실제 인증 체계 없음 | 전체 API | `docs/specs/v0.1.0/001-class-enrollment-core/spec.md` (범위 외) |
| EnrollmentController 미구현 | `enrollment` 도메인/애플리케이션/인프라 계층은 구현됐으나 presentation(REST API) 계층이 아직 없음 | `enrollment` 도메인 | `docs/specs/v0.1.0/001-class-enrollment-core/tasks.md` T012 |
| SC-001~SC-009 테스트 일부 미작성 | tasks.md T013~T017 (도메인/유스케이스 단위 테스트, SC-003·005·006·007 통합 테스트) 미완료 | 전체 | `docs/specs/v0.1.0/001-class-enrollment-core/tasks.md` |
| MongoDB·Redis 미도입 | research.md에 계획은 있으나 실제 연결 코드·설정 없음 | 인프라 | 003/004 spec에서 도입 예정 |

## 7. 갱신 이력

| 날짜 | commit | 갱신 내용 | 관련 spec |
|---|---|---|---|
| 2026-07-09 | (커밋 전) | 최초 작성 (그린필드, 소스 코드 없음) | — |
| 2026-07-09 | `44f3192` | 001 spec 진행 중간 상태 반영 — Course·Enrollment 도메인/애플리케이션/인프라 구현 완료, 프로젝트 구조·레이어·핵심 모듈·데이터 흐름·도메인 모델을 실제 코드 기준으로 갱신. EnrollmentController 및 일부 테스트(T013~T017) 미완료 상태를 기술 부채로 기록 | `docs/specs/v0.1.0/001-class-enrollment-core/` |
