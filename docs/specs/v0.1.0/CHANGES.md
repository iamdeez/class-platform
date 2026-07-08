# Changes: v0.1.0

## [001-class-enrollment-core] 구현 완료

**변경 파일**:

인프라/설정:
- `build.gradle.kts`: Kotlin 1.9.25 + Spring Boot 3.3.4 의존성 (JPA, Validation, Flyway, MySQL 드라이버, MockK, springmockk, Testcontainers)
- `settings.gradle.kts`: 프로젝트명 설정
- `docker-compose.yml`, `.env.example`: 로컬 MySQL 8 컨테이너
- `src/main/resources/application.yml`: datasource, JPA(`ddl-auto: validate`), Flyway 설정
- `src/main/resources/db/migration/V1__create_course_table.sql`, `V2__create_enrollment_table.sql`: `course`(price CHECK ≥ 0), `enrollment`((course_id, user_id) UNIQUE + FK) 테이블
- `src/main/kotlin/com/classplatform/ClassPlatformApplication.kt`: Spring Boot 진입점

공통(`common`):
- `UserId.kt`: 사용자 식별자 값 객체(`@JvmInline value class`, 양수 검증)
- `exception/DomainException.kt`: 도메인 예외 카테고리 4종(`ResourceNotFoundException`/`DuplicateResourceException`/`InvalidStateException`/`ForbiddenActionException`) — 새 도메인 예외 추가 시 핸들러 수정 불필요
- `GlobalExceptionHandler.kt`: 카테고리별 예외 + `IllegalArgumentException`/`MethodArgumentNotValidException` → HTTP 상태 코드 매핑(`@RestControllerAdvice`)
- `ApiResponse.kt`: 공통 응답 래퍼(`{data, error}`)
- `PageRequest.kt`, `PageResult.kt`: 프레임워크 비의존 페이지네이션 값 객체 (NFR-001 최대 100건을 `PageRequest` 생성자에서 강제)

Course 도메인(`course/*`):
- `domain/Course.kt`: 애그리거트 루트. `register()`/`reconstitute()` 팩토리, title/price 불변식(`require`), `publish()`/`close()`로만 상태 전이
- `domain/CourseStatus.kt`: DRAFT→PUBLISHED→CLOSED 단방향 전이 규칙(`canTransitionTo`)
- `domain/CourseRepository.kt`: 포트 인터페이스
- `domain/exception/{CourseNotFoundException, InvalidCourseStatusTransitionException, CourseNotEnrollableException}.kt`
- `infrastructure/{CourseJpaEntity, CourseJpaRepository, CourseRepositoryImpl}.kt`: JPA 어댑터. `createdAt`/`updatedAt`은 `@CreationTimestamp`(+`updatable=false`)/`@UpdateTimestamp`로 관리해 merge 시 null 덮어쓰기 방지
- `application/{RegisterCourseUseCase, GetCourseUseCase, ListCoursesUseCase, PublishCourseUseCase, CloseCourseUseCase}.kt`
- `presentation/CourseController.kt`, `presentation/dto/CourseDtos.kt`: REST API 4종 (`POST/GET /api/courses`, `GET/PATCH /api/courses/{id}`)

Enrollment 도메인(`enrollment/*`):
- `domain/Enrollment.kt`: 애그리거트 루트. `create()`/`reconstitute()` 팩토리, `cancel()`로만 상태 전이(이중 취소 방지)
- `domain/EnrollmentStatus.kt`
- `domain/EnrollmentRepository.kt`: 포트 인터페이스
- `domain/exception/{DuplicateEnrollmentException, EnrollmentNotFoundException, EnrollmentAccessDeniedException, EnrollmentCancellationNotAllowedException, InvalidEnrollmentStatusException}.kt`
- `infrastructure/{EnrollmentJpaEntity, EnrollmentJpaRepository, EnrollmentRepositoryImpl}.kt`: `saveAndFlush()`로 INSERT를 즉시 실행시켜 `(course_id, user_id)` 유니크 제약 위반을 이 메서드 안에서 잡아 `DuplicateEnrollmentException`으로 변환
- `application/{EnrollUseCase, CancelEnrollmentUseCase, ListMyEnrollmentsUseCase}.kt`: `EnrollUseCase`는 `CourseRepository`로 PUBLISHED 여부 확인, `CancelEnrollmentUseCase`는 소유자(403)·CLOSED 여부(409) 확인, `ListMyEnrollmentsUseCase`는 ACTIVE만 필터링
- `presentation/EnrollmentController.kt`, `presentation/dto/EnrollmentDtos.kt`: REST API 3종 (`POST /api/courses/{id}/enrollments`, `DELETE /api/enrollments/{id}`, `GET /api/users/me/enrollments`)

테스트 (18개 파일):
- 단위: `UserIdTest`, `GlobalExceptionHandlerTest`, `CourseTest`(SC-001/002/008), `{Register,Get,List,Publish,Close}CourseUseCaseTest`, `{Enroll(SC-004/009),CancelEnrollment,ListMyEnrollments}UseCaseTest`
- 슬라이스(`@WebMvcTest`): `CourseControllerTest`, `EnrollmentControllerTest`
- 통합(Testcontainers MySQL): `CourseRepositoryImplIT`, `EnrollmentRepositoryImplIT`, `CourseControllerIT`(SC-003), `EnrollmentControllerIT`(SC-005/007), `EnrollmentConcurrencyIT`(SC-006, 100 스레드 동시 신청)

**후속 작업 시 주의사항**:
- **FR-008 소유자 권한 체크 미구현**: spec.md FR-008은 "강의를 등록한 강사는" 상태를 전이시킬 수 있다고 명시하지만, 이를 검증하는 SC가 spec.md에 없어 `PublishCourseUseCase`/`CloseCourseUseCase`에 소유자 확인 로직을 넣지 않았다. 실제로 필요해지면 spec.md에 SC를 먼저 추가한 뒤 구현할 것.
- **취소 후 재신청 미지원**: `enrollment` 테이블의 `(course_id, user_id)` UNIQUE 제약이 CANCELLED 레코드에도 적용되어 취소한 강의에 동일 사용자가 재신청 불가하다 (MySQL이 조건부 유니크 인덱스를 지원하지 않아 발생한 트레이드오프 — `001-class-enrollment-core/research.md` 참조).
- **인증·인가 미구현**: 전 API가 `X-User-Id` 헤더를 신뢰하는 방식으로만 사용자를 식별한다 (spec.md에 범위 외로 명시).
- **plan.md 상태 코드 일관성 수정**: 구현 중 "강의가 PUBLISHED가 아닐 때 수강 신청 시도"의 응답 코드가 plan.md에는 `400`으로 적혀 있었으나, 같은 성격의 Course 자체 상태 전이 거부(SC-008)는 이미 `409`로 설계되어 있어 불일치를 발견했다. `CourseNotEnrollableException`을 `InvalidStateException` 카테고리(409)로 구현하고 plan.md도 `409`로 수정했다. 002 spec 이후 "상태 기반 거부는 409"라는 관례를 유지할 것.
- **MongoDB·Redis·MyBatis 미도입**: 001은 MySQL 단일 의존이며, 002(커뮤니티) spec에서 MongoDB를, 003(캐싱) spec에서 Redis를 도입할 예정이다.

## [001-class-enrollment-core] 후속 보완: Course description HTML sanitize 추가

Apidog로 API를 테스트하던 중 `description`에 실제 강의 상세 페이지 수준의 HTML(이미지 포함)을 입력하는 사례가 나오면서, 저장 전 XSS 방어가 없다는 점을 발견해 보완했다. 새 기능 추가가 아닌 기존 001 구현의 보안 강화이므로 별도 spec 없이 직접 반영했다.

**변경 파일**:
- `build.gradle.kts`: `org.jsoup:jsoup:1.22.2` 추가
- `common/HtmlSanitizer.kt` (신규): `Safelist.relaxed()`를 기반으로 `figure`/`figcaption`/`section` 태그와 `img`/`figure`/`section`의 `style`/`class` 속성을 허용 추가하고, `img[src]`는 `http`/`https` 프로토콜만 허용하도록 확장. `<script>`, `on*` 이벤트 핸들러, `javascript:` 프로토콜 등은 기본적으로 제거됨
- `course/application/RegisterCourseUseCase.kt`: `Course.register()` 호출 전 `description`을 `HtmlSanitizer.sanitize()`로 정제
- `test/.../common/HtmlSanitizerTest.kt` (신규): script 제거, onerror 제거, javascript: 프로토콜 제거, figure/style 등 허용 서식 보존 4개 케이스 검증

**설계 근거**: sanitize 로직을 `Course` 도메인(`domain/Course.kt`)이 아닌 `RegisterCourseUseCase`(application 레이어)에 배치했다. 도메인 계층은 Spring/JPA는 물론 jsoup 같은 외부 라이브러리에도 의존하지 않는 순수 Kotlin 상태를 유지하기 위함이며(001 최초 구현 시 확립한 원칙), "신뢰할 수 없는 외部 입력을 유효한 도메인 커맨드로 변환"하는 책임은 애플리케이션 레이어의 역할이라는 기존 아키텍처 결정과도 일치한다. `reconstitute()` 경로(DB에서 읽어온 이미 정제된 값)는 재sanitize하지 않는다.

**후속 작업 시 주의사항**:
- 002 spec(커뮤니티 게시글) 구현 시에도 사용자 입력 HTML을 저장하는 필드가 있다면 동일하게 `HtmlSanitizer`를 재사용할 것.
- 현재 허용 태그/속성 목록은 001에서 실제로 관측된 CKEditor 스타일 콘텐츠(figure, img, style, class)에 맞춘 것으로, 향후 필요한 태그가 늘어나면 `HtmlSanitizer.safelist`에만 추가하면 된다.
