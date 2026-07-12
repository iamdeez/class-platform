# Tasks: complex-query-statistics

> Branch: 004-complex-query-statistics | Date: 2026-07-12 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 003 완료 후 "좋아요 0건 게시글 랭킹 부재", "Redis 동기화 유실 가능성" 등을 확인했다. 004는 001의 MySQL(Course/Enrollment)만 다루므로 002/003(MongoDB/Redis)과는 직접적인 연관이 없다.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. 의존 관계가 있는 태스크는 반드시 선행 태스크 완료 후 실행한다.

### Phase 1. 기반 작업 — Enrollment 도메인 확장

- [x] **T001** — EnrollmentStatus 확장 + Enrollment 도메인 모델 수정
  - 구현 파일: `enrollment/domain/EnrollmentStatus.kt`(수정), `enrollment/domain/Enrollment.kt`(수정)
  - 관련 요구사항: `FR-004`, `FR-006`, `FR-007`
  - 상세: `EnrollmentStatus`에 `COMPLETED` 추가, `CourseStatus.canTransitionTo()`와 동일한 패턴으로 전이표 캡슐화(`ACTIVE→COMPLETED`, `ACTIVE→CANCELLED`만 허용). `Enrollment`에 `price: BigDecimal` 필드 추가(`create()`/`reconstitute()` 시그니처 변경), `complete()` 메서드 추가(전이 위반 시 `InvalidEnrollmentStatusException`). 기존 `cancel()`도 새 전이표 기반으로 재작성
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다. `Enrollment.reconstitute()` 호출부(테스트 fixture 포함) 전수 확인 및 수정 — 확인 완료
  - **구현 노트**: `price`는 Post의 `likeCount`/`viewCount`와 달리 `create()`/`reconstitute()`에 기본값을 주지 않았다. 매출 계산의 핵심 도메인 데이터이므로 누락 시 조용히 0으로 채워지는 것을 방지하기 위해 모든 호출부를 명시적으로 고치도록 강제하는 Breaking Change로 설계했다.
  - **구현 중 발견한 이슈**: `price` 필드 추가가 `EnrollUseCase.kt`, `EnrollmentRepositoryImpl.kt`(운영 코드)와 `CancelEnrollmentUseCaseTest.kt`, `ListMyEnrollmentsUseCaseTest.kt`, `EnrollmentRepositoryImplIT.kt`, `EnrollmentControllerTest.kt`(테스트) 등 9개 호출부에 연쇄 컴파일 오류를 유발했다(research.md에서 사전 예상한 영향 범위와 일치). T004/T005와 분리해서 진행하면 빌드가 계속 깨진 상태로 남으므로 T001+T004+T005를 하나의 구현 단위로 묶어 진행했다.

- [x] **T002** `[P]` — Gradle 의존성 추가
  - 구현 파일: `build.gradle.kts`
  - 상세: `mybatis-spring-boot-starter` 추가(Spring Boot 3.3.4 호환 버전)
  - 완료 기준: `./gradlew build` 성공 — 확인 완료 (`mybatis-spring-boot-starter:3.0.3`, `./gradlew dependencies --configuration compileClasspath`로 의존성 해석 확인)

- [x] **T003** — V3 마이그레이션 작성 (T002와 무관, 병렬 가능) `[P]`
  - 구현 파일: `src/main/resources/db/migration/V3__add_enrollment_price_and_completed_status.sql`
  - 관련 요구사항: `FR-007`
  - 상세: `enrollment.price DECIMAL(10,2) NOT NULL DEFAULT 0` 추가 후, `UPDATE enrollment e JOIN course c ON e.course_id = c.id SET e.price = c.price`로 기존 행 백필. `status` 컬럼은 `VARCHAR(20)`이라 `COMPLETED` 값 저장에 스키마 변경 불필요
  - 완료 기준: `./gradlew bootRun` 시 Flyway 마이그레이션이 오류 없이 적용된다 — 확인 완료 (Testcontainers 기반 `EnrollmentRepositoryImplIT` 실행 시 V3 마이그레이션이 오류 없이 적용됨을 함께 확인)

### Phase 2. 핵심 구현 — Enrollment 확장

- [x] **T004** — EnrollUseCase 수정: 가격 스냅샷 전달 (T001, T003 완료 후)
  - 구현 파일: `enrollment/application/EnrollUseCase.kt`(수정)
  - 관련 요구사항: `FR-007`
  - 상세: 기존에 조회하던 `course.price`를 `Enrollment.create(courseId, userId, course.price)`에 전달
  - 완료 기준: 기존 `EnrollUseCaseTest` 수정 + 가격이 정확히 스냅샷되는지 검증하는 케이스 추가, 단위 테스트 통과 — 확인 완료 (`신청 시점의 강의 가격이 Enrollment에 스냅샷으로 저장된다` 케이스 추가)

- [x] **T005** — EnrollmentJpaEntity/RepositoryImpl 매핑 갱신 (T001, T003 완료 후, T004와 병렬 가능) `[P]`
  - 구현 파일: `enrollment/infrastructure/EnrollmentJpaEntity.kt`(수정), `enrollment/infrastructure/EnrollmentRepositoryImpl.kt`(수정)
  - 관련 요구사항: `FR-007`
  - 상세: `price` 컬럼 매핑 추가, 도메인 ↔ JPA 엔티티 변환에 반영
  - 완료 기준: Testcontainers 통합 테스트로 `price` 저장·조회 왕복 확인 — 확인 완료 (`price가 저장 후 조회 시 동일하게 왕복된다` 케이스 추가, `BigDecimal.compareTo()` 기반 스케일 무관 비교)

- [x] **T006** — CompleteEnrollmentUseCase 구현 (T001, T005 완료 후)
  - 구현 파일: `enrollment/application/CompleteEnrollmentUseCase.kt`(신규)
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-006`
  - 상세: `enrollmentRepository.findById()`(404) → `courseRepository.findById(enrollment.courseId)`(404) → `course.instructorId == requesterId` 검증(403) → `enrollment.complete()`(전이 위반 시 409) → `save()`
  - 완료 기준: 단위 테스트(MockK)로 정상 완료·404·403·409(이미 취소/이미 완료) 케이스 모두 통과 — 확인 완료 (5개 케이스 모두 PASS, `InvalidEnrollmentStatusException`은 `InvalidStateException` 상속으로 `GlobalExceptionHandler`에서 자동으로 409 매핑됨을 확인)

- [x] **T007** — EnrollmentController에 완료 처리 엔드포인트 추가 (T006 완료 후)
  - 구현 파일: `enrollment/presentation/EnrollmentController.kt`(수정), `enrollment/presentation/dto/EnrollmentDtos.kt`(수정)
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-006`
  - 상세: `POST /api/enrollments/{enrollmentId}/complete` 추가
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인 — 확인 완료 (`CompleteEnrollmentResponse(enrollmentId, status)`, plan.md 인터페이스 계약 `200 {enrollmentId, status}`과 일치)

### Phase 3. 핵심 구현 — Statistics

- [x] **T008** — CourseRepository.findAllByInstructorId() 추가 (T002와 무관, 병렬 가능) `[P]`
  - 구현 파일: `course/domain/CourseRepository.kt`(수정), `course/infrastructure/CourseRepositoryImpl.kt`(수정)
  - 관련 요구사항: `FR-001`
  - 상세: 강사 소유 강의 목록 조회(상태 무관, DRAFT 포함 — 대시보드는 강사 본인 것이므로 공개 여부와 무관하게 전부 보여줌)
  - 완료 기준: Testcontainers 통합 테스트로 강사별 강의 목록 조회 확인 — 확인 완료 (`CourseJpaRepository.findAllByInstructorId` derived query, DRAFT/PUBLISHED 혼합 + 타 강사 강의 배제 시나리오로 검증)

- [x] **T009** — CourseStatistics 값 객체 + CourseStatisticsRepository 포트 (T002 완료 후)
  - 구현 파일: `statistics/domain/CourseStatistics.kt`(신규), `statistics/domain/CourseStatisticsRepository.kt`(신규)
  - 관련 요구사항: `FR-001`, `FR-002`
  - 상세: `CourseStatistics(courseId, title, studentCount, revenue, completionRate, cancellationRate)`. 포트는 `findAllByInstructorId(instructorId): List<CourseStatistics>`, `findByCourseId(courseId): CourseStatistics?`
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다 — 확인 완료 (`java.math.BigDecimal`, `common.UserId` 외 프레임워크 import 없음)

- [x] **T010** — CourseStatisticsMapper(MyBatis) + CourseStatisticsRepositoryImpl (T002, T003, T009 완료 후)
  - 구현 파일: `statistics/infrastructure/CourseStatisticsMapper.kt`(신규), `statistics/infrastructure/resources/CourseStatisticsMapper.xml`(신규), `statistics/infrastructure/CourseStatisticsRepositoryImpl.kt`(신규)
  - 관련 요구사항: `FR-001`, `FR-002`, `NFR-001`
  - 상세: plan.md의 GROUP BY 집계 쿼리(`CASE WHEN` 조건부 카운트/합계)를 MyBatis Mapper로 구현. `CourseStatisticsRepositoryImpl`이 원시 카운트를 받아 `completionRate`/`cancellationRate`를 계산(분모 0 방어)
  - 완료 기준: Testcontainers 통합 테스트로 다양한 상태 조합(ACTIVE/COMPLETED/CANCELLED 혼합)에서 집계 값이 정확한지 확인. 수강생이 없는 강의의 0/0 방어 케이스 포함 — 확인 완료 (혼합 상태 집계, 0/0 방어, 강사별 필터링, 존재하지 않는 강의 조회 4개 케이스 모두 PASS)
  - **구현 노트**: XML 매퍼는 `src/main/resources/mybatis/mapper/`에 위치시키고 `application.yml`에 `mybatis.mapper-locations`(명시적 classpath 경로)와 `mybatis.configuration.map-underscore-to-camel-case: true`(snake_case 컬럼 → camelCase 필드 자동 매핑)를 추가했다. `@Mapper` 인터페이스는 `@SpringBootApplication`(`com.classplatform`) 하위 패키지라 별도 `@MapperScan` 없이 자동 스캔된다. 통합 테스트는 `@DataJpaTest`(JPA 슬라이스, MyBatis 자동 구성 미포함)가 아닌 001의 `CourseControllerIT` 패턴을 따라 `@SpringBootTest` 전체 컨텍스트로 작성했다 — MongoDB/Redis는 연결 없이도 빈 등록만으로 컨텍스트가 정상 기동됨을 확인.

- [x] **T011** — GetInstructorStatisticsUseCase, GetCourseStatisticsUseCase (T010 완료 후)
  - 구현 파일: `statistics/application/GetInstructorStatisticsUseCase.kt`(신규), `statistics/application/GetCourseStatisticsUseCase.kt`(신규)
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 상세: `GetCourseStatisticsUseCase`는 `courseRepository.findById()`로 소유권을 먼저 검증(403/404) 후 통계 조회
  - 완료 기준: 단위 테스트(MockK)로 정상 조회·403·404 케이스 통과 — 확인 완료 (4개 케이스 모두 PASS)
  - **구현 노트**: 403 판정에 필요한 `CourseAccessDeniedException`(`ForbiddenActionException` 상속)이 기존에 없어 `course/domain/exception`에 신규 추가했다. `GetCourseStatisticsUseCase`는 소유권 검증 후 `findByCourseId()`가 이론상 null을 반환할 수 없지만(직전에 `courseRepository.findById()`로 존재를 확인했으므로), 방어적으로 `CourseNotFoundException`을 재사용해 처리했다.

- [ ] **T012** — StatisticsController 및 DTO 구현 (T011 완료 후)
  - 구현 파일: `statistics/presentation/StatisticsController.kt`(신규), `statistics/presentation/dto/StatisticsDtos.kt`(신규)
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 상세: `GET /api/instructors/me/statistics`, `GET /api/courses/{courseId}/statistics`
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

### Phase 4. 테스트 (SC-XXX 검증)

- [ ] **T013** — SC-001, SC-002, SC-003 통합 테스트 (T012 완료 후)
  - 테스트 파일: `statistics/presentation/StatisticsControllerIT.kt`
  - 검증 대상: `SC-001`, `SC-002`, `SC-003`
  - 시나리오: 강사가 여러 강의·다양한 수강 상태를 시드한 뒤 목록/상세 통계 조회, 비담당 강사의 조회 시도 시 403 확인

- [ ] **T014** — SC-004, SC-005, SC-006 통합 테스트 (T007 완료 후, T013과 병렬 가능) `[P]`
  - 테스트 파일: `enrollment/presentation/EnrollmentCompletionIT.kt`
  - 검증 대상: `SC-004`, `SC-005`, `SC-006`
  - 시나리오: 정상 완료 처리 후 상태 확인, 비담당 강사의 완료 처리 시도(403), 이미 취소/완료된 수강 재완료 시도(409)

- [ ] **T015** — SC-007, SC-008 통합 테스트 (T010 완료 후, 위 태스크들과 병렬 가능) `[P]`
  - 테스트 파일: `statistics/infrastructure/CourseStatisticsRepositoryImplIT.kt`
  - 검증 대상: `SC-007`, `SC-008`
  - 시나리오: 강의 가격 변경 전후로 신청된 두 수강의 매출 합이 각 신청 시점 가격 기준으로 정확한지 확인, 취소된 수강이 매출에서 제외되는지 확인

- [ ] **T016** `[P]` — SC-009, SC-010 단위 테스트
  - 테스트 파일: `statistics/infrastructure/CourseStatisticsRepositoryImplTest.kt` (또는 비율 계산 로직을 별도 함수로 분리 시 그 단위 테스트)
  - 검증 대상: `SC-009`, `SC-010`
  - 시나리오: 완료율·취소율 계산 산식 검증(정상 케이스 + 분모 0 케이스)

## 구현 완료 기준

- [ ] 모든 태스크 체크박스가 완료 처리되었다.
- [ ] `./gradlew test`가 전체 PASSED를 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다.
