# Diff: 001-class-enrollment-core

## 커밋 메시지 한 줄 요약

- **KO**: 클래스(강의) 등록·조회·수강신청 핵심 도메인을 DDD 기반으로 구현하고, SC-001~SC-009 전체를 단위·슬라이스·통합(Testcontainers) 테스트로 검증했다.
- **EN**: Implement the class registration/enrollment core domain with DDD, and verify SC-001~SC-009 via unit, slice, and Testcontainers integration tests.

## 변경 요약

Gradle(Kotlin+Spring Boot 3) 프로젝트 스켈레톤부터 시작해 `common`(값 객체·공통 예외·응답 래퍼) → `course` 애그리거트(도메인/인프라/애플리케이션/프레젠테이션 4계층) → `enrollment` 애그리거트(동일 4계층) 순서로 구현했다. 도메인 계층은 Spring/JPA 의존 없이 순수 Kotlin으로 유지했고(constitution P-001), JPA 엔티티-도메인 객체 변환은 각 `RepositoryImpl` 어댑터가 전담한다. 동시성 정합성(NFR-002)은 애플리케이션 락이 아니라 `enrollment` 테이블의 `(course_id, user_id)` DB 유니크 제약 + `saveAndFlush()`로 보장한다. spec.md의 수용 기준(SC-001~SC-009) 전체가 최소 1개 이상의 테스트로 매핑되어 있다.

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `.env.example` | +4 | -0 |
| `build.gradle.kts` | +54 | -0 |
| `docker-compose.yml` | +22 | -0 |
| `settings.gradle.kts` | +1 | -0 |
| `src/main/kotlin/com/classplatform/ClassPlatformApplication.kt` | +11 | -0 |
| `src/main/kotlin/com/classplatform/common/ApiResponse.kt` | +18 | -0 |
| `src/main/kotlin/com/classplatform/common/GlobalExceptionHandler.kt` | +41 | -0 |
| `src/main/kotlin/com/classplatform/common/PageRequest.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/common/PageResult.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/common/UserId.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/common/exception/DomainException.kt` | +12 | -0 |
| `src/main/kotlin/com/classplatform/course/application/CloseCourseUseCase.kt` | +18 | -0 |
| `src/main/kotlin/com/classplatform/course/application/GetCourseUseCase.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/course/application/ListCoursesUseCase.kt` | +16 | -0 |
| `src/main/kotlin/com/classplatform/course/application/PublishCourseUseCase.kt` | +18 | -0 |
| `src/main/kotlin/com/classplatform/course/application/RegisterCourseUseCase.kt` | +17 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/Course.kt` | +60 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/CourseRepository.kt` | +12 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/CourseStatus.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/exception/CourseNotEnrollableException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/exception/CourseNotFoundException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/exception/InvalidCourseStatusTransitionException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/course/infrastructure/CourseJpaEntity.kt` | +47 | -0 |
| `src/main/kotlin/com/classplatform/course/infrastructure/CourseJpaRepository.kt` | +10 | -0 |
| `src/main/kotlin/com/classplatform/course/infrastructure/CourseRepositoryImpl.kt` | +53 | -0 |
| `src/main/kotlin/com/classplatform/course/presentation/CourseController.kt` | +99 | -0 |
| `src/main/kotlin/com/classplatform/course/presentation/dto/CourseDtos.kt` | +39 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/application/CancelEnrollmentUseCase.kt` | +35 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/application/EnrollUseCase.kt` | +26 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/application/ListMyEnrollmentsUseCase.kt` | +15 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/Enrollment.kt` | +29 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/EnrollmentRepository.kt` | +11 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/EnrollmentStatus.kt` | +6 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/exception/DuplicateEnrollmentException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/exception/EnrollmentAccessDeniedException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/exception/EnrollmentCancellationNotAllowedException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/exception/EnrollmentNotFoundException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/domain/exception/InvalidEnrollmentStatusException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentJpaEntity.kt` | +40 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentJpaRepository.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentRepositoryImpl.kt` | +48 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/presentation/EnrollmentController.kt` | +63 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/presentation/dto/EnrollmentDtos.kt` | +12 | -0 |
| `src/main/resources/application.yml` | +15 | -0 |
| `src/main/resources/db/migration/V1__create_course_table.sql` | +12 | -0 |
| `src/main/resources/db/migration/V2__create_enrollment_table.sql` | +11 | -0 |
| `src/test/kotlin/com/classplatform/common/GlobalExceptionHandlerTest.kt` | +68 | -0 |
| `src/test/kotlin/com/classplatform/common/UserIdTest.kt` | +20 | -0 |
| `src/test/kotlin/com/classplatform/course/application/CloseCourseUseCaseTest.kt` | +39 | -0 |
| `src/test/kotlin/com/classplatform/course/application/GetCourseUseCaseTest.kt` | +36 | -0 |
| `src/test/kotlin/com/classplatform/course/application/ListCoursesUseCaseTest.kt` | +27 | -0 |
| `src/test/kotlin/com/classplatform/course/application/PublishCourseUseCaseTest.kt` | +39 | -0 |
| `src/test/kotlin/com/classplatform/course/application/RegisterCourseUseCaseTest.kt` | +29 | -0 |
| `src/test/kotlin/com/classplatform/course/domain/CourseTest.kt` | +54 | -0 |
| `src/test/kotlin/com/classplatform/course/infrastructure/CourseRepositoryImplIT.kt` | +75 | -0 |
| `src/test/kotlin/com/classplatform/course/presentation/CourseControllerIT.kt` | +70 | -0 |
| `src/test/kotlin/com/classplatform/course/presentation/CourseControllerTest.kt` | +106 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/EnrollmentConcurrencyIT.kt` | +90 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/CancelEnrollmentUseCaseTest.kt` | +71 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/EnrollUseCaseTest.kt` | +59 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/ListMyEnrollmentsUseCaseTest.kt` | +28 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentRepositoryImplIT.kt` | +76 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/presentation/EnrollmentControllerIT.kt` | +88 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/presentation/EnrollmentControllerTest.kt` | +83 | -0 |

**합계**: 64 files changed, 2032 insertions(+)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(설계 문서 초기화 직후, 001 구현 착수 직전)과 재생성 명령만 기록한다.

- base commit: `f47dc63` ([docs] 프로젝트 설계 문서 및 규칙 초기화)
- 재생성 명령: `git diff f47dc63..7951a25 -- src/ build.gradle.kts settings.gradle.kts docker-compose.yml .env.example`
