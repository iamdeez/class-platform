# Plan: complex-query-statistics

> Branch: 004-complex-query-statistics | Date: 2026-07-12 | Spec: [spec.md](./spec.md)

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

- [x] **P-001 도메인 순수성**: `statistics/domain`, `enrollment/domain`은 MyBatis 타입에 의존하지 않는다. `CourseStatisticsRepository`는 도메인 인터페이스이고, MyBatis Mapper 구현은 infrastructure에 위치한다.
- [x] **P-002 성능 원칙**: NFR-001 자체가 N+1 방지 요구사항이다. 강사의 모든 강의 통계를 단일 GROUP BY 쿼리로 조회해 강의 수만큼 쿼리가 늘어나지 않도록 한다.
- [x] **P-003 호환성 원칙**: `Enrollment.create()`/`reconstitute()` 시그니처 변경은 도메인 내부 API로 외부 REST 계약과 무관하다. `EnrollmentStatus`에 `COMPLETED` 추가는 additive change다. 기존 `EnrollmentResponse` 스키마는 변경하지 않는다(완료 여부 노출이 필요하면 필드 추가로 처리 — 기존 필드 유지).
- [x] **P-004 테스트 원칙**: spec.md의 모든 FR(001~007)이 SC-001~SC-010에 매핑되어 있다.
- [x] **P-005 스펙 범위 원칙**: 강의 검색·필터링, 통계 캐싱, 관리자 전체 통계, 게시글 통계, 완료 취소, 기간별 추이는 spec.md "범위 외"에 명시했고 본 plan에 포함하지 않는다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **언어 / 런타임**: Kotlin 1.9.25, Spring Boot 3.3.4, JDK 17 (기존과 동일)
- **주요 의존성 (신규)**: `mybatis-spring-boot-starter`(3.x대, Spring Boot 3.3.4 호환 버전) — `course`/`enrollment`가 모두 MySQL(001)에 있어 단일 DB 내 JOIN 집계에 사용
- **DB**: MySQL 8.0(001과 동일 인스턴스), 신규 Flyway 마이그레이션 `V3`
- **테스트 프레임워크**: JUnit5 + MockK + Testcontainers-MySQL(기존 재사용)

## 사전 영향도 분석 결과

research.md의 "영향 파일 목록" 표를 기준으로 한다. 핵심 요약:

| 파일 | 변경 유형 |
|---|---|
| `enrollment/domain/EnrollmentStatus.kt` | 수정 (`COMPLETED` 추가, `canTransitionTo()` 전이표) |
| `enrollment/domain/Enrollment.kt` | 수정 (`price` 필드, `complete()` 메서드) |
| `enrollment/application/EnrollUseCase.kt` | 수정 (가격 스냅샷 전달) |
| `enrollment/application/CompleteEnrollmentUseCase.kt` | 신규 |
| `enrollment/infrastructure/{EnrollmentJpaEntity, EnrollmentRepositoryImpl}.kt` | 수정 |
| `src/main/resources/db/migration/V3__add_enrollment_price_and_completed_status.sql` | 신규 |
| `course/domain/CourseRepository.kt`, `course/infrastructure/CourseRepositoryImpl.kt` | 수정 (`findAllByInstructorId`) |
| `statistics/domain/{CourseStatistics, CourseStatisticsRepository}.kt` | 신규 |
| `statistics/infrastructure/{CourseStatisticsMapper(+.xml), CourseStatisticsRepositoryImpl}.kt` | 신규 |
| `statistics/application/{GetInstructorStatistics, GetCourseStatistics}UseCase.kt` | 신규 |
| `statistics/presentation/StatisticsController.kt`, `dto/*.kt` | 신규 |
| `build.gradle.kts`, `src/main/resources/application.yml` | 수정 |

## 핵심 설계

### EnrollmentStatus 상태 전이

`CourseStatus.canTransitionTo()`와 동일한 패턴으로 명시적 전이표를 둔다.

```
ACTIVE ──cancel()──→ CANCELLED
ACTIVE ──complete()──→ COMPLETED
```

`CANCELLED`/`COMPLETED`는 모두 종단 상태(terminal)다. `COMPLETED`에서 `cancel()`을 호출하거나 `CANCELLED`에서 `complete()`를 호출하면 `InvalidEnrollmentStatusException`을 던진다(기존 `cancel()`의 가드를 `canTransitionTo()` 기반으로 재작성).

### 수강 신청 시 가격 스냅샷 (FR-007)

```
EnrollUseCase.execute(courseId, userId)
      ↓ courseRepository.findById(courseId) — 이미 조회하던 course.price를 재사용
      ↓ Enrollment.create(courseId, userId, course.price)
      ↓ enrollmentRepository.save()
```

별도 조회 없이 기존 흐름에 `course.price` 전달 한 줄만 추가된다.

### 수강 완료 처리 (FR-004~006)

```
CompleteEnrollmentUseCase.execute(enrollmentId, requesterId)
      ↓ enrollmentRepository.findById(enrollmentId) — 404
      ↓ courseRepository.findById(enrollment.courseId) — 404
      ↓ course.instructorId == requesterId 검증 — 아니면 403(FR-005)
      ↓ enrollment.complete() — 이미 CANCELLED/COMPLETED면 409(FR-006, InvalidEnrollmentStatusException)
      ↓ enrollmentRepository.save()
```

### 통계 집계 (FR-001~003)

```
GetInstructorStatisticsUseCase.execute(instructorId)
      ↓ CourseStatisticsRepository.findAllByInstructorId(instructorId) — MyBatis GROUP BY 단일 쿼리
      ↓ List<CourseStatistics> 반환 (courseId, title, studentCount, revenue, completionRate, cancellationRate)

GetCourseStatisticsUseCase.execute(courseId, requesterId)
      ↓ courseRepository.findById(courseId) — 404
      ↓ course.instructorId == requesterId 검증 — 아니면 403(FR-003)
      ↓ CourseStatisticsRepository.findByCourseId(courseId) — 단일 강의 집계
```

`completionRate`/`cancellationRate` 계산은 0으로 나누기를 방지하기 위해 분모가 0이면 0을 반환한다(수강생이 없는 강의).

### MyBatis 집계 쿼리 (research.md 기반 확정)

`CourseStatisticsMapper.xml`에 아래 형태의 쿼리를 둔다(강사 목록 조회·단일 강의 조회는 `WHERE` 절만 다른 동일 쿼리를 재사용).

```sql
SELECT
  c.id AS course_id,
  c.title AS title,
  COUNT(CASE WHEN e.status IN ('ACTIVE', 'COMPLETED') THEN 1 END) AS student_count,
  COALESCE(SUM(CASE WHEN e.status IN ('ACTIVE', 'COMPLETED') THEN e.price END), 0) AS revenue,
  COUNT(CASE WHEN e.status = 'COMPLETED' THEN 1 END) AS completed_count,
  COUNT(CASE WHEN e.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
  COUNT(e.id) AS total_count
FROM course c
LEFT JOIN enrollment e ON e.course_id = c.id
WHERE c.instructor_id = #{instructorId}
  <if test="courseId != null">AND c.id = #{courseId}</if>
GROUP BY c.id, c.title
```

`CourseStatisticsRepositoryImpl`이 `completed_count`/`student_count`, `cancelled_count`/`total_count`로 비율을 계산해 `CourseStatistics` 값 객체로 변환한다(분모 0 방어 포함).

## 인터페이스 계약

사용자 식별은 001~003과 동일하게 `X-User-Id` 헤더를 신뢰한다.

| Method | Path | 요청 | 응답 | 관련 FR/SC |
|---|---|---|---|---|
| GET | `/api/instructors/me/statistics` | - | `200 {items: [{courseId, title, studentCount, revenue, completionRate, cancellationRate}]}` | FR-001, SC-001 |
| GET | `/api/courses/{courseId}/statistics` | - | `200 {courseId, title, studentCount, revenue, completionRate, cancellationRate}` / `403` / `404` | FR-002, FR-003, SC-002, SC-003 |
| POST | `/api/enrollments/{enrollmentId}/complete` | - | `200 {enrollmentId, status}` / `403` / `404` / `409` | FR-004~006, SC-004~006 |

## 데이터 모델

### `enrollment` 테이블 (필드 추가, `V3` 마이그레이션)

| 필드 | 타입 | 비고 |
|---|---|---|
| `price` | DECIMAL(10,2) NOT NULL | 신청 시점 강의 가격 스냅샷. 기존 행은 마이그레이션 시점 `course.price`로 백필(연습 프로젝트 특성상 운영 데이터 없음을 감안한 단순화) |
| `status` | VARCHAR(20) | 기존 컬럼 재사용, 애플리케이션 레벨에서 `COMPLETED` 값 허용(컬럼 정의 자체는 변경 불필요) |

### `CourseStatistics` (도메인 값 객체, 영속화 대상 아님)

| 필드 | 타입 | 비고 |
|---|---|---|
| `courseId` | Long | |
| `title` | String | |
| `studentCount` | Long | ACTIVE + COMPLETED |
| `revenue` | BigDecimal | ACTIVE + COMPLETED의 `price` 합 |
| `completionRate` | Double | `completed / studentCount`, `studentCount == 0`이면 0 |
| `cancellationRate` | Double | `cancelled / totalCount`, `totalCount == 0`이면 0 |

## 테스트 전략

| SC 식별자 | 테스트 유형 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 통합 테스트 (Testcontainers MySQL) | 강사가 여러 강의를 등록하고 각기 다른 수강 현황을 만든 뒤 목록 통계 조회 | 강의 3개, 수강 다수 | 각 강의별 정확한 집계 값 |
| SC-002 | 통합 테스트 | 단일 강의 상세 통계 조회 | 강의 1개 | 정확한 집계 값 |
| SC-003 | 통합 테스트 (API) | 다른 강사의 강의 통계 조회 시도 | 다른 instructorId | 403 |
| SC-004 | 통합 테스트 (API) | 강사가 수강 완료 처리 | 정상 요청 | 이후 조회에서 status=COMPLETED |
| SC-005 | 통합 테스트 (API) | 비담당 강사의 완료 처리 시도 | 다른 instructorId | 403 |
| SC-006 | 단위 테스트 (domain) + 통합 테스트 | 이미 CANCELLED/COMPLETED인 수강 재완료 시도 | 각각 | `InvalidEnrollmentStatusException` → 409 |
| SC-007 | 통합 테스트 | 강의 가격 변경 후 과거 수강의 매출 재계산 | 가격 변경 전후 신청 2건 | 매출 = 각 신청 시점 가격의 합 |
| SC-008 | 통합 테스트 | 취소된 수강이 매출에서 제외되는지 확인 | ACTIVE 1건 + CANCELLED 1건 | 매출 = ACTIVE분만 |
| SC-009 | 단위 테스트 (domain 또는 application) | 완료율 계산(분모 0 포함) | 다양한 조합 | 산식대로 계산, 0/0은 0 |
| SC-010 | 단위 테스트 | 취소율 계산 | 다양한 조합 | 산식대로 계산 |

## 기타 고려사항

- **완료율/취소율 계산 위치**: SQL에서 나눗셈까지 처리하지 않고(DB 방언별 정수 나눗셈 함정 방지), `CourseStatisticsRepositoryImpl`(Kotlin)에서 원시 카운트를 받아 비율을 계산한다 — 0 나눗셈 방어 로직을 테스트하기도 더 쉽다.
- **기존 `enrollment` 데이터 백필**: `V3` 마이그레이션에서 `UPDATE enrollment e JOIN course c ON e.course_id = c.id SET e.price = c.price`로 기존 행을 일괄 백필한다.
- **MyBatis와 JPA 공존**: 001의 `CourseRepository`/`EnrollmentRepository`는 JPA를 계속 사용하고, `statistics`만 MyBatis를 쓴다. 동일 프로젝트에서 두 영속성 기술이 같은 MySQL 인스턴스를 공유하는 것은 Spring Boot 표준 지원 범위이며 별도 트랜잭션 매니저 조정이 필요 없다(둘 다 동일 `DataSource`를 사용).
