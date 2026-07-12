# Diff: 004-complex-query-statistics

## 커밋 메시지 한 줄 요약

- **KO**: 강사 대시보드용 강의별 수강생 수·매출·완료율·취소율 통계를 MyBatis 단일 GROUP BY 쿼리로 집계하고, Enrollment에 완료 상태와 가격 스냅샷을 추가해 SC-001~SC-010 전체를 단위·슬라이스·통합(Testcontainers MySQL) 테스트로 검증했다.
- **EN**: Aggregate per-course instructor dashboard statistics (student count, revenue, completion/cancellation rate) via a single MyBatis GROUP BY query, add a completion status and price snapshot to Enrollment, and verify SC-001~SC-010 via unit, slice, and Testcontainers (MySQL) integration tests.

## 변경 요약

001~003의 로드맵에서 예고된 마지막 단계로, MySQL 단일 저장소 내에서 `course`·`enrollment`를 JOIN하는 복잡한 조건부 집계 쿼리를 다루기 위해 네 번째 영속성 기술인 MyBatis를 도입했다. `CASE WHEN` 기반 GROUP BY 단일 쿼리로 강사 소유 강의별 수강생 수·매출·완료된 수/취소된 수/전체 수를 한 번에 조회하고, 완료율·취소율은 SQL이 아닌 Kotlin 레이어(`CourseStatisticsRepositoryImpl`)에서 분모 0을 방어하며 계산한다(NFR-001 N+1 없는 단일 쿼리, 001의 `CourseRepository`/`EnrollmentRepository`는 JPA를 유지). 매출을 강의 가격 변경에 영향받지 않게 하기 위해 `Enrollment`에 신청 시점 가격 스냅샷(`price`)을 추가했으며, `likeCount`/`viewCount` 등 003에서 기본값 있는 필드로 확장했던 것과 달리 이번엔 기본값 없는 Breaking Change로 설계해 모든 생성 경로가 명시적으로 값을 채우도록 강제했다. 완료율 반영을 위해 `EnrollmentStatus`에 `COMPLETED`를 추가하고 `CourseStatus.canTransitionTo()`와 동일한 패턴으로 전이표를 캡슐화했다. 구현 과정에서 001부터 있던 `ListMyEnrollmentsUseCase`의 `status == ACTIVE` 필터가 새로 추가된 COMPLETED 상태까지 함께 걸러내던 버그를 발견해 `status != CANCELLED`로 수정했다(SC-004 검증 중 발견). spec.md의 수용 기준(SC-001~SC-010) 전체가 최소 1개 이상의 테스트로 매핑되어 있다.

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `build.gradle.kts` | +1 | -0 |
| `src/main/resources/application.yml` | +5 | -0 |
| `src/main/resources/db/migration/V3__add_enrollment_price_and_completed_status.sql` | +6 | -0 |
| `src/main/resources/mybatis/mapper/CourseStatisticsMapper.xml` | +33 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/CourseRepository.kt` | +3 | -0 |
| `src/main/kotlin/com/classplatform/course/domain/exception/CourseAccessDeniedException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/course/infrastructure/CourseJpaRepository.kt` | +2 | -0 |
| `src/main/kotlin/com/classplatform/course/infrastructure/CourseRepositoryImpl.kt` | +3 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/application/CompleteEnrollmentUseCase.kt` | +28 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/application/EnrollUseCase.kt` | +1 | -1 |
| `src/main/kotlin/com/classplatform/enrollment/application/ListMyEnrollmentsUseCase.kt` | +1 | -1 |
| `src/main/kotlin/com/classplatform/enrollment/domain/Enrollment.kt` | +21 | -8 |
| `src/main/kotlin/com/classplatform/enrollment/domain/EnrollmentStatus.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentJpaEntity.kt` | +4 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentRepositoryImpl.kt` | +2 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/presentation/EnrollmentController.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/enrollment/presentation/dto/EnrollmentDtos.kt` | +2 | -0 |
| `src/main/kotlin/com/classplatform/statistics/application/GetCourseStatisticsUseCase.kt` | +25 | -0 |
| `src/main/kotlin/com/classplatform/statistics/application/GetInstructorStatisticsUseCase.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/statistics/domain/CourseStatistics.kt` | +12 | -0 |
| `src/main/kotlin/com/classplatform/statistics/domain/CourseStatisticsRepository.kt` | +9 | -0 |
| `src/main/kotlin/com/classplatform/statistics/infrastructure/CourseStatisticsMapper.kt` | +22 | -0 |
| `src/main/kotlin/com/classplatform/statistics/infrastructure/CourseStatisticsRepositoryImpl.kt` | +30 | -0 |
| `src/main/kotlin/com/classplatform/statistics/presentation/StatisticsController.kt` | +49 | -0 |
| `src/main/kotlin/com/classplatform/statistics/presentation/dto/StatisticsDtos.kt` | +14 | -0 |
| `src/test/kotlin/com/classplatform/course/infrastructure/CourseRepositoryImplIT.kt` | +14 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/CancelEnrollmentUseCaseTest.kt` | +3 | -3 |
| `src/test/kotlin/com/classplatform/enrollment/application/CompleteEnrollmentUseCaseTest.kt` | +85 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/EnrollUseCaseTest.kt` | +13 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/application/ListMyEnrollmentsUseCaseTest.kt` | +15 | -2 |
| `src/test/kotlin/com/classplatform/enrollment/infrastructure/EnrollmentRepositoryImplIT.kt` | +17 | -3 |
| `src/test/kotlin/com/classplatform/enrollment/presentation/EnrollmentCompletionIT.kt` | +111 | -0 |
| `src/test/kotlin/com/classplatform/enrollment/presentation/EnrollmentControllerTest.kt` | +18 | -2 |
| `src/test/kotlin/com/classplatform/statistics/application/GetCourseStatisticsUseCaseTest.kt` | +54 | -0 |
| `src/test/kotlin/com/classplatform/statistics/application/GetInstructorStatisticsUseCaseTest.kt` | +30 | -0 |
| `src/test/kotlin/com/classplatform/statistics/infrastructure/CourseStatisticsRepositoryImplIT.kt` | +136 | -0 |
| `src/test/kotlin/com/classplatform/statistics/infrastructure/CourseStatisticsRepositoryImplTest.kt` | +85 | -0 |
| `src/test/kotlin/com/classplatform/statistics/presentation/StatisticsControllerIT.kt` | +112 | -0 |
| `src/test/kotlin/com/classplatform/statistics/presentation/StatisticsControllerTest.kt` | +65 | -0 |

**합계**: 39 files changed, 1072 insertions(+), 20 deletions(-) (spec 문서 자체 변경분 제외)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(003 완료 직후, 004 설계 착수 직전)과 재생성 명령만 기록한다.

- base commit: `678ee0f` ([test] Phase 4 SC-XXX 검증 테스트 및 003 spec 구현 완료 기록)
- 재생성 명령: `git diff 678ee0f -- src/main/kotlin/com/classplatform/course src/main/kotlin/com/classplatform/enrollment src/main/kotlin/com/classplatform/statistics src/test/kotlin/com/classplatform/course src/test/kotlin/com/classplatform/enrollment src/test/kotlin/com/classplatform/statistics build.gradle.kts src/main/resources/application.yml src/main/resources/db/migration src/main/resources/mybatis`
