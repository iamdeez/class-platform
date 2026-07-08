# Plan: class-enrollment-core

> Branch: 001-class-enrollment-core | Date: 2026-07-09 | Spec: [spec.md](./spec.md)

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

`class-platform/.claude/constitution.md`가 아직 존재하지 않으므로 전역 규칙 기본 4개 조항을 Gates로 사용한다.

- [x] 성능 원칙: 그린필드 초기 구현이며 알려진 성능 저하 요인(N+1, 불필요 동기 I/O 등)을 포함하지 않는다. 목록 조회는 페이지네이션(NFR-001)으로 제한한다.
- [x] 호환성 원칙: 신규 프로젝트이므로 깨질 기존 통합 코드가 없다.
- [x] 테스트 원칙: FR-001~FR-009 전체가 SC-001~SC-009에 매핑되어 검증 시나리오를 갖췄다 (아래 테스트 전략 참조).
- [x] 스펙 범위 원칙: 커뮤니티·캐싱·통계·인증·배포는 spec.md "범위 외"에 명시했고 본 plan에도 포함하지 않는다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **언어 / 런타임**: Kotlin 1.9, JDK 17
- **프레임워크**: Spring Boot 3.x (spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation)
- **빌드 도구**: Gradle (Kotlin DSL, `build.gradle.kts`)
- **DB**: MySQL 8 (로컬 개발은 Docker Compose로 기동)
- **주요 의존성**: Kotlin JPA 플러그인(`kotlin-jpa`, `kotlin-allopen`), Jackson Kotlin 모듈
- **테스트 프레임워크**: JUnit5 + MockK(단위 테스트 mocking) + Testcontainers(MySQL 통합 테스트) + RestAssured 또는 MockMvc(API 테스트)

## 사전 영향도 분석 결과

그린필드 프로젝트이므로 전 파일이 신규 생성이다. `research.md`의 패키지 구조를 기준으로 한다.

### 영향 파일 목록

| 파일 | 변경 유형 | 영향 내용 |
|---|---|---|
| `build.gradle.kts`, `settings.gradle.kts` | 신규 | Gradle 프로젝트 초기화, 의존성 선언 |
| `src/main/kotlin/com/classplatform/course/domain/Course.kt` | 신규 | 강의 애그리거트 루트, 상태 전이 규칙(FR-008) 포함 |
| `src/main/kotlin/com/classplatform/course/domain/CourseStatus.kt` | 신규 | DRAFT/PUBLISHED/CLOSED enum, 전이 가능 여부 판정 |
| `src/main/kotlin/com/classplatform/course/domain/CourseRepository.kt` | 신규 | 도메인 리포지토리 인터페이스 |
| `src/main/kotlin/com/classplatform/course/application/*.kt` | 신규 | RegisterCourseUseCase, PublishCourseUseCase, CloseCourseUseCase, ListCoursesUseCase, GetCourseUseCase |
| `src/main/kotlin/com/classplatform/course/infrastructure/*.kt` | 신규 | CourseJpaEntity, CourseJpaRepository, CourseRepositoryImpl |
| `src/main/kotlin/com/classplatform/course/presentation/*.kt` | 신규 | CourseController, 요청/응답 DTO |
| `src/main/kotlin/com/classplatform/enrollment/domain/Enrollment.kt` | 신규 | 수강신청 애그리거트, 상태(ACTIVE/CANCELLED) |
| `src/main/kotlin/com/classplatform/enrollment/domain/EnrollmentRepository.kt` | 신규 | 도메인 리포지토리 인터페이스 |
| `src/main/kotlin/com/classplatform/enrollment/application/*.kt` | 신규 | EnrollUseCase, CancelEnrollmentUseCase, ListMyEnrollmentsUseCase |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/*.kt` | 신규 | EnrollmentJpaEntity, EnrollmentJpaRepository, EnrollmentRepositoryImpl (유니크 제약 매핑 포함) |
| `src/main/kotlin/com/classplatform/enrollment/presentation/*.kt` | 신규 | EnrollmentController, 요청/응답 DTO |
| `src/main/kotlin/com/classplatform/common/*.kt` | 신규 | UserId 값 객체, 공통 예외 → HTTP 상태 매핑(`@RestControllerAdvice`), API 응답 래퍼 |
| `src/main/resources/application.yml` | 신규 | DB 연결 설정 |
| `src/main/resources/db/migration/V1__create_course_table.sql`, `V2__create_enrollment_table.sql` | 신규 | Flyway 마이그레이션 (course·enrollment 테이블, 유니크 제약) |
| `docker-compose.yml` | 신규 | 로컬 MySQL 컨테이너 |

## 핵심 설계

### 도메인 모델

- **Course (애그리거트 루트)**: `id`, `title`, `description`, `price`(0 이상, 도메인 불변식), `instructorId`, `status: CourseStatus`. `publish()`, `close()` 메서드가 상태 전이 규칙(FR-008: DRAFT→PUBLISHED→CLOSED만 허용)을 캡슐화하며, 규칙 위반 시 `InvalidCourseStatusTransitionException`을 던진다.
- **CourseStatus**: `enum class CourseStatus { DRAFT, PUBLISHED, CLOSED }` + 허용 전이 맵을 도메인 내부에 정의.
- **Enrollment (애그리거트 루트)**: `id`, `courseId`, `userId`, `status: EnrollmentStatus(ACTIVE, CANCELLED)`. 생성 시점에 대상 Course가 PUBLISHED인지 애플리케이션 계층(`EnrollUseCase`)에서 확인 후 생성한다 (Enrollment 자체는 Course 상태를 모른다 — 애그리거트 경계 분리).

### 유스케이스 흐름 (예: 수강 신청)

```
EnrollmentController.enroll(courseId, userId)
      ↓
EnrollUseCase.execute(courseId, userId)
      ↓ 1. CourseRepository.findById(courseId) → PUBLISHED 아니면 CourseNotEnrollableException
      ↓ 2. Enrollment.create(courseId, userId) → EnrollmentRepository.save()
      ↓ 3. DB 유니크 제약(course_id, user_id) 위반 시 DataIntegrityViolationException 캐치 → DuplicateEnrollmentException 변환
      ↓
공통 예외 핸들러가 도메인 예외 → HTTP 상태 코드로 매핑 (400/404/409)
```

### 레이어드 아키텍처 원칙 (NFR-003)

`domain` 패키지는 Spring·JPA 애노테이션을 포함하지 않는다. JPA 매핑은 `infrastructure` 패키지의 별도 Entity 클래스(`CourseJpaEntity` 등)가 담당하고, `Repository` 인터페이스 구현체가 도메인 객체 ↔ JPA 엔티티 변환을 수행한다. 이를 통해 도메인 로직(상태 전이, 불변식)은 프레임워크 없이 순수 Kotlin 단위 테스트로 검증 가능하다.

## 인터페이스 계약

사용자 식별은 `X-User-Id` 요청 헤더로 전달된다고 가정한다 (spec.md "범위 외" — 실제 인증은 미구현, 헤더 값을 신뢰).

| Method | Path | 요청 | 응답 | 관련 FR/SC |
|---|---|---|---|---|
| POST | `/api/courses` | `{title, description, price}` | `201 {courseId}` / `400` | FR-001, SC-001, SC-002 |
| GET | `/api/courses?page=&size=` | 쿼리 파라미터 | `200 {items[], page, totalCount}` (PUBLISHED만) | FR-002, NFR-001, SC-003 |
| GET | `/api/courses/{courseId}` | - | `200 {course}` / `404` | FR-003 |
| PATCH | `/api/courses/{courseId}/status` | `{action: "PUBLISH"|"CLOSE"}` | `200` / `409`(잘못된 전이) | FR-008, SC-008 |
| POST | `/api/courses/{courseId}/enrollments` | `X-User-Id` 헤더 | `201 {enrollmentId}` / `409`(비공개) / `409`(중복) | FR-004, FR-006, SC-004, SC-005, SC-006 |
| DELETE | `/api/enrollments/{enrollmentId}` | `X-User-Id` 헤더 | `204` / `403`(타인 신청) / `409`(CLOSED 강의) | FR-005, SC-007 |
| GET | `/api/users/me/enrollments` | `X-User-Id` 헤더 | `200 {items[]}` | FR-007 |

## 데이터 모델

### `course` 테이블

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `title` | VARCHAR(200) | NOT NULL |
| `description` | TEXT | NULL 허용 |
| `price` | DECIMAL(10,2) | NOT NULL, CHECK(price >= 0) |
| `instructor_id` | BIGINT | NOT NULL |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'DRAFT' |
| `created_at`, `updated_at` | DATETIME | NOT NULL |

### `enrollment` 테이블

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `course_id` | BIGINT | NOT NULL, FK → course.id |
| `user_id` | BIGINT | NOT NULL |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' |
| `created_at`, `updated_at` | DATETIME | NOT NULL |
| — | UNIQUE | `(course_id, user_id)` — NFR-002 정합성 보장. 취소(CANCELLED) 후 동일 강의 재신청은 001 범위에서 미지원 (research.md 참조). |

## 테스트 전략

| SC 식별자 | 테스트 유형 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 단위 테스트 (domain) | 제목/가격 누락으로 Course 생성 시도 | title="" 또는 price=null | 검증 예외 발생 |
| SC-002 | 단위 테스트 (domain) | Course 생성 직후 상태 확인 | 정상 입력 | status == DRAFT |
| SC-003 | 통합 테스트 (API) | 목록 조회 시 DRAFT/CLOSED 제외 확인 | DRAFT 1건, PUBLISHED 2건, CLOSED 1건 시드 | 응답 items 2건만 PUBLISHED |
| SC-004 | 단위 테스트 (application) | DRAFT/CLOSED 강의에 EnrollUseCase 호출 | status=DRAFT 인 courseId | CourseNotEnrollableException |
| SC-005 | 통합 테스트 (API) | 동일 사용자 동일 강의 2회 신청 | 순차 2회 요청 | 2번째 요청 409 |
| SC-006 | 통합 테스트 (동시성) | 동일 사용자·강의에 스레드풀로 동시 요청 N회 | 100개 동시 요청 | enrollment row 정확히 1건 |
| SC-007 | 통합 테스트 (API) | 취소 후 내 신청 목록 재조회 | 신청 → 취소 → 목록 조회 | 목록에서 제외 |
| SC-008 | 단위 테스트 (domain) | DRAFT 상태에서 close() 직접 호출 | DRAFT Course | InvalidCourseStatusTransitionException |
| SC-009 | 단위 테스트 (application) | CLOSED 강의에 EnrollUseCase 호출 | status=CLOSED 인 courseId | CourseNotEnrollableException |

## 기타 고려사항

- **Flyway 도입**: 스키마 변경 이력을 명시적으로 관리하기 위해 `ddl-auto: validate` + Flyway 마이그레이션 조합을 사용한다 (JPA `ddl-auto: update`는 운영 위험이 있어 지양 — 이 자체가 실무 관행을 보여주는 포트폴리오 포인트).
- **동시성 통합 테스트 구현 팁**: `ExecutorService` + `CountDownLatch`로 N개 스레드가 동시에 동일 요청을 보내도록 구성하고, 이후 DB에 남은 row 수를 검증한다.
- **커뮤니티(002)와의 연결점**: `common` 패키지의 `UserId` 값 객체는 002 spec에서도 재사용될 공유 커널이므로, 강의 도메인에 종속되지 않는 위치(`common`)에 둔다.
