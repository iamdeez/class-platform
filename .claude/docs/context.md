# Project Context

> 이 문서는 프로젝트의 **현재 상태를 묘사**하는 살아있는 참조 문서다.
> 새로운 spec 설계 전 반드시 읽어 프로젝트 구조·흐름·용어를 숙지한다.
>
> - **갱신 시점**: spec 구현·검증 완료 후 갱신한다.
> - **작성 원칙**: 현재 코드베이스의 사실만 기록한다. 미래 계획이나 설계 의도는 spec.md에 작성한다.
> - **기준 커밋**: 이 문서는 최초 작성 시점(2026-07-09) 기준이며, 아직 소스 코드가 없는 상태다.

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
- **현재 버전**: v0.1.0 (001 spec 설계 완료, 구현 착수 전)
- **주요 기술 스택 (계획, 아직 미구현)**: Kotlin, Spring Boot 3.x, JPA + MyBatis, MySQL + MongoDB + Redis, Gradle(Kotlin DSL). 상세 근거는 `docs/specs/v0.1.0/001-class-enrollment-core/research.md` 참조.

## 2. 프로젝트 구조

**현재 실제 상태**: 소스 코드가 아직 없다. spec 설계 문서만 존재한다.

```
class-platform/
├── CLAUDE.md
├── .claude/docs/           ← constitution.md, context.md(본 문서), infra.md
└── docs/specs/
    └── v0.1.0/
        └── 001-class-enrollment-core/
            ├── spec.md
            ├── research.md
            ├── plan.md
            └── tasks.md
```

계획된 소스 패키지 구조(`com.classplatform.course`, `com.classplatform.enrollment`, `com.classplatform.common`)는 `docs/specs/v0.1.0/001-class-enrollment-core/plan.md`의 "사전 영향도 분석 결과"에 정의되어 있으며, 001 spec 구현 완료 후 이 섹션을 실제 구조로 갱신한다.

### 핵심 모듈 목록

[미파악 — 아직 코드 없음. 001 spec 구현 완료 후 작성]

## 3. 이벤트 및 데이터 흐름

[미파악 — 아직 코드 없음. 001 spec 구현 완료 후 작성]

## 4. 도메인 모델

**계획된 모델** (아직 구현 전, `docs/specs/v0.1.0/001-class-enrollment-core/plan.md` 기준):

| 엔티티 | 설명 | 주요 속성 |
|---|---|---|
| `Course` | 강의 애그리거트 루트 | id, title, description, price, instructorId, status(DRAFT/PUBLISHED/CLOSED) |
| `Enrollment` | 수강신청 애그리거트 루트 | id, courseId, userId, status(ACTIVE/CANCELLED) |

구현 완료 후 실제 코드 기준으로 이 섹션을 갱신한다.

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

## 7. 갱신 이력

| 날짜 | commit | 갱신 내용 | 관련 spec |
|---|---|---|---|
| 2026-07-09 | (커밋 전) | 최초 작성 (그린필드, 소스 코드 없음) | — |
