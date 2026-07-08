# Tasks: class-enrollment-core

> Branch: 001-class-enrollment-core | Date: 2026-07-09 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 기본 4개 조항 모두 통과.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 그린필드 프로젝트, CHANGES.md 없음 (해당 없음).

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. 의존 관계가 있는 태스크는 반드시 선행 태스크 완료 후 실행한다.

### Phase 1. 기반 작업

- [x] **T001** — Gradle 프로젝트 초기화 (Kotlin + Spring Boot 3, JDK 17)
  - 구현 파일: `build.gradle.kts`, `settings.gradle.kts`
  - 관련 요구사항: 없음 (인프라 스켈레톤)
  - 상세: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, kotlin-jpa/kotlin-allopen 플러그인, Flyway, MySQL 드라이버, JUnit5/MockK/Testcontainers 의존성 선언
  - 완료 기준: `./gradlew build`가 (테스트 없이도) 성공한다

- [x] **T002** `[P]` — 로컬 MySQL 기동 구성
  - 구현 파일: `docker-compose.yml`, `src/main/resources/application.yml`
  - 관련 요구사항: 없음
  - 상세: MySQL 8 컨테이너 정의, datasource 연결 정보 설정 (ddl-auto: validate)
  - 완료 기준: `docker compose up -d` 후 애플리케이션이 DB 연결에 성공한다

- [x] **T003** — Flyway 마이그레이션 작성 (T001 완료 후)
  - 구현 파일: `src/main/resources/db/migration/V1__create_course_table.sql`, `V2__create_enrollment_table.sql`
  - 관련 요구사항: `NFR-002` (enrollment 유니크 제약)
  - 상세: plan.md 데이터 모델 섹션의 컬럼·제약을 그대로 반영. `enrollment`에 `(course_id, user_id)` 유니크 인덱스 포함
  - 완료 기준: 애플리케이션 기동 시 마이그레이션이 자동 적용되고 두 테이블이 생성된다

- [x] **T004** `[P]` — 공통 모듈 구현
  - 구현 파일: `common/UserId.kt`, `common/GlobalExceptionHandler.kt`, `common/ApiResponse.kt`
  - 관련 요구사항: 없음 (횡단 관심사)
  - 상세: `UserId` 값 객체(Kotlin `@JvmInline value class`), 도메인 예외 → HTTP 상태 코드 매핑(`@RestControllerAdvice`), 공통 응답 래퍼
  - 완료 기준: 컴파일 성공, 단위 테스트로 예외 → 상태 코드 매핑 확인

### Phase 2. 핵심 구현 — Course

- [x] **T005** — Course 도메인 모델 구현 (T003 완료 후)
  - 구현 파일: `course/domain/Course.kt`, `course/domain/CourseStatus.kt`, `course/domain/exception/*.kt`
  - 관련 요구사항: `FR-001`, `FR-008`
  - 상세: `publish()`/`close()` 메서드에 DRAFT→PUBLISHED→CLOSED 전이 규칙 캡슐화, price 음수 방지 불변식
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다

- [x] **T006** — CourseRepository 인터페이스 + JPA 구현체 (T005 완료 후)
  - 구현 파일: `course/domain/CourseRepository.kt`, `course/infrastructure/CourseJpaEntity.kt`, `course/infrastructure/CourseJpaRepository.kt`, `course/infrastructure/CourseRepositoryImpl.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 상세: 도메인 객체 ↔ JPA 엔티티 변환, PUBLISHED 상태 필터링 페이지 조회 메서드 포함
  - 완료 기준: 통합 테스트(Testcontainers)로 저장·조회 왕복이 확인된다

- [x] **T007** — Course 유스케이스 구현 (T006 완료 후)
  - 구현 파일: `course/application/RegisterCourseUseCase.kt`, `PublishCourseUseCase.kt`, `CloseCourseUseCase.kt`, `ListCoursesUseCase.kt`, `GetCourseUseCase.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`, `FR-008`
  - 완료 기준: 각 유스케이스 단위 테스트(MockK 기반) 통과

- [x] **T008** — CourseController 및 DTO 구현 (T007 완료 후)
  - 구현 파일: `course/presentation/CourseController.kt`, `course/presentation/dto/*.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`, `FR-008`
  - 상세: plan.md 인터페이스 계약 표의 Course 관련 엔드포인트 구현
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

### Phase 3. 핵심 구현 — Enrollment

- [x] **T009** `[P]` — Enrollment 도메인 모델 구현 (T004 완료 후)
  - 구현 파일: `enrollment/domain/Enrollment.kt`, `enrollment/domain/EnrollmentStatus.kt`, `enrollment/domain/exception/*.kt`
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-006`, `FR-007`
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다

- [x] **T010** — EnrollmentRepository 인터페이스 + JPA 구현체 (T009 완료 후)
  - 구현 파일: `enrollment/domain/EnrollmentRepository.kt`, `enrollment/infrastructure/EnrollmentJpaEntity.kt`, `enrollment/infrastructure/EnrollmentJpaRepository.kt`, `enrollment/infrastructure/EnrollmentRepositoryImpl.kt`
  - 관련 요구사항: `FR-006`, `NFR-002`
  - 상세: `DataIntegrityViolationException` → `DuplicateEnrollmentException` 변환 로직 포함
  - 완료 기준: 통합 테스트로 유니크 제약 위반 시 변환 예외가 발생함을 확인

- [x] **T011** — Enrollment 유스케이스 구현 (T010, T007 완료 후)
  - 구현 파일: `enrollment/application/EnrollUseCase.kt`, `CancelEnrollmentUseCase.kt`, `ListMyEnrollmentsUseCase.kt`
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-006`, `FR-007`, `FR-009`
  - 상세: `EnrollUseCase`는 `CourseRepository`로 대상 강의 상태를 확인한 후 신청 생성
  - 완료 기준: 각 유스케이스 단위 테스트(MockK 기반) 통과

- [x] **T012** — EnrollmentController 및 DTO 구현 (T011 완료 후)
  - 구현 파일: `enrollment/presentation/EnrollmentController.kt`, `enrollment/presentation/dto/*.kt`
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-007`
  - 상세: plan.md 인터페이스 계약 표의 Enrollment 관련 엔드포인트 구현, `X-User-Id` 헤더 파싱
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

### Phase 4. 테스트 (SC-XXX 검증)

- [x] **T013** `[P]` — SC-001, SC-002, SC-008 도메인 단위 테스트
  - 테스트 파일: `course/domain/CourseTest.kt`
  - 검증 대상: `SC-001`, `SC-002`, `SC-008`
  - 시나리오: 필수값 누락 생성 실패, 생성 직후 상태 DRAFT 확인, DRAFT→CLOSED 직접 전이 거부

- [ ] **T014** `[P]` — SC-004, SC-009 유스케이스 단위 테스트
  - 테스트 파일: `enrollment/application/EnrollUseCaseTest.kt`
  - 검증 대상: `SC-004`, `SC-009`
  - 시나리오: DRAFT/CLOSED 강의에 신청 시도 시 예외 발생 (MockK로 CourseRepository stub)

- [ ] **T015** — SC-003 통합 테스트 (T008 완료 후)
  - 테스트 파일: `course/presentation/CourseControllerIT.kt`
  - 검증 대상: `SC-003`
  - 시나리오: DRAFT 1건·PUBLISHED 2건·CLOSED 1건 시드 후 목록 조회 → PUBLISHED 2건만 반환 확인

- [ ] **T016** — SC-005, SC-007 통합 테스트 (T012 완료 후)
  - 테스트 파일: `enrollment/presentation/EnrollmentControllerIT.kt`
  - 검증 대상: `SC-005`, `SC-007`
  - 시나리오: 동일 사용자 순차 2회 신청 → 2번째 409, 신청 후 취소 → 내 목록에서 제외 확인

- [ ] **T017** — SC-006 동시성 통합 테스트 (T012 완료 후)
  - 테스트 파일: `enrollment/EnrollmentConcurrencyIT.kt`
  - 검증 대상: `SC-006`
  - 시나리오: `ExecutorService` + `CountDownLatch`로 동일 사용자·강의에 100개 동시 요청 발생 → DB에 enrollment row 정확히 1건 확인

## 구현 완료 기준

- [ ] 모든 태스크 체크박스가 완료 처리되었다.
- [ ] `./gradlew test`가 전체 PASSED를 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다.
