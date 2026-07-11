# Research: complex-query-statistics

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

### 클래스·모듈 계층 구조

- `Enrollment`(`enrollment/domain/Enrollment.kt`)는 `private constructor` + 정적 팩토리(`create()`/`reconstitute()`)로 생성이 제한된 concrete 클래스다. 현재 필드는 `id, courseId, userId, status`뿐이며, 가격 스냅샷이나 완료 여부가 없다.
- `EnrollmentStatus`(`enrollment/domain/EnrollmentStatus.kt`)는 `ACTIVE`/`CANCELLED` 2값 enum이다. `Course`의 `CourseStatus`(`DRAFT`/`PUBLISHED`/`CLOSED`, `canTransitionTo()`로 단방향 전이 규칙 캡슐화)와 동일한 패턴을 이미 프로젝트에서 쓰고 있다 — 완료 상태 추가도 이 패턴을 따르는 것이 일관적이다.
- `Enrollment.cancel()`은 `status == CANCELLED`면 예외를 던지는 가드만 있다. 상태 전이 규칙이 `CourseStatus.canTransitionTo()`처럼 명시적 전이표로 캡슐화되어 있지 않고 `cancel()` 메서드 내부에 하드코딩되어 있다.
- `EnrollUseCase.execute()`는 이미 `courseRepository.findById(courseId)`로 `Course`(가격 포함)를 조회한 뒤 `Enrollment.create(courseId, userId)`를 호출한다 — `course.price`를 그대로 `Enrollment.create()`에 전달하기만 하면 가격 스냅샷을 추가할 수 있다(별도 조회 불필요).
- `EnrollmentJpaEntity`/`CourseJpaEntity`는 각각 `enrollment`/`course` 테이블에 매핑되며, 둘 다 MySQL(001)에 있다 — 002(MongoDB)·003(Redis)와 달리 이번 spec의 집계 대상 데이터는 하나의 관계형 DB 안에 있어 JOIN 기반 집계 쿼리가 기술적으로 자연스럽다.
- `CourseRepository`에는 강사 ID로 강의 목록을 조회하는 메서드가 없다(`findAllByStatus`만 존재). 강사 대시보드용 조회 메서드 신규 필요.

### 영향 파일 목록

| 파일 | 변경 유형 | 비고 |
|---|---|---|
| `enrollment/domain/EnrollmentStatus.kt` | 수정 | `COMPLETED` 추가, `CourseStatus`처럼 `canTransitionTo()` 전이표 캡슐화 |
| `enrollment/domain/Enrollment.kt` | 수정 | `price: BigDecimal` 필드 추가, `complete()` 메서드 추가(전이 가드), `create()`/`reconstitute()` 시그니처 변경 |
| `enrollment/application/EnrollUseCase.kt` | 수정 | `Enrollment.create(courseId, userId, course.price)`로 가격 전달 |
| `enrollment/application/CompleteEnrollmentUseCase.kt` | 신규 | 강사가 특정 수강을 완료 처리 |
| `enrollment/infrastructure/EnrollmentJpaEntity.kt` | 수정 | `price` 컬럼 추가 |
| `enrollment/infrastructure/EnrollmentRepositoryImpl.kt` | 수정 | 매핑 갱신 |
| `src/main/resources/db/migration/V3__add_enrollment_price_and_completed_status.sql` | 신규 | `enrollment.price` 컬럼 추가(기존 행은 트레이드오프 — 아래 "엣지 케이스" 참조) |
| `course/domain/CourseRepository.kt` | 수정 | `findAllByInstructorId(instructorId): List<Course>` 추가 |
| `statistics/domain/CourseStatistics.kt` | 신규 | 프레임워크 비의존 값 객체(courseId, title, studentCount, revenue, completionRate, cancellationRate) |
| `statistics/domain/CourseStatisticsRepository.kt` | 신규 | 집계 쿼리 포트 인터페이스 |
| `statistics/infrastructure/CourseStatisticsMapper.kt` (+`.xml`) | 신규 | MyBatis Mapper — JOIN + GROUP BY 집계 쿼리 |
| `statistics/infrastructure/CourseStatisticsRepositoryImpl.kt` | 신규 | Mapper 결과 → 도메인 값 객체 변환 |
| `statistics/application/{GetInstructorStatistics,GetCourseStatistics}UseCase.kt` | 신규 | 강사 소유권 검증 포함 |
| `statistics/presentation/StatisticsController.kt`, `dto/*.kt` | 신규 | REST API |
| `build.gradle.kts` | 수정 | `mybatis-spring-boot-starter` 추가 |
| `src/main/resources/application.yml` | 수정 | `mybatis.mapper-locations` 등 설정(필요 시) |

## 기술 선택 조사

### 집계 쿼리 기술: MyBatis

- 001 spec.md에서 "004 (예정): 복잡 조회·통계 (MyBatis)"로 이미 계획되어 있었다(`docs/specs/v0.1.0/001-class-enrollment-core/research.md`: "복잡한 통계·조회는 004 spec에서 별도 도입 예정"). 이 spec에서 그 계획을 실행한다.
- 대안으로 Spring Data JPA의 `@Query(nativeQuery = true)`나 JPQL도 가능하지만, `CASE WHEN` 기반 조건부 집계(활성/완료/취소별 카운트·합계를 한 번의 GROUP BY로)처럼 SQL 표현력이 중요한 쿼리에서 MyBatis의 XML 매퍼가 동적 SQL(`<if>`, `<where>`, `<foreach>`)과 원시 SQL 제어력을 더 직접적으로 제공한다. 채용공고가 요구하는 "복잡한 쿼리 작성" 역량을 보여주는 것이 이 spec의 목적이므로, JPA 네이티브 쿼리보다 MyBatis를 명시적으로 도입하는 편이 spec의 의도에 맞는다.
- `course`·`enrollment` 모두 MySQL(001) 소속이라 단일 DB 내 JOIN으로 충분하다 — 002(MongoDB)·003(Redis)처럼 이종 저장소 간 집계가 필요 없어 MyBatis 하나로 이번 spec의 조회 요구사항을 전부 처리할 수 있다.

### 통계 집계 SQL 개요 (개념 검증, 최종 쿼리는 구현 시 확정)

```sql
SELECT
  c.id,
  c.title,
  COUNT(CASE WHEN e.status IN ('ACTIVE', 'COMPLETED') THEN 1 END) AS student_count,
  COALESCE(SUM(CASE WHEN e.status IN ('ACTIVE', 'COMPLETED') THEN e.price END), 0) AS revenue,
  COUNT(CASE WHEN e.status = 'COMPLETED' THEN 1 END) AS completed_count,
  COUNT(CASE WHEN e.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
  COUNT(e.id) AS total_count
FROM course c
LEFT JOIN enrollment e ON e.course_id = c.id
WHERE c.instructor_id = #{instructorId}
GROUP BY c.id, c.title
```

`completionRate = completed_count / student_count`(0으로 나누기 방지 필요), `cancellationRate = cancelled_count / total_count`는 애플리케이션 레이어(또는 SQL의 안전한 나눗셈)에서 계산한다. `LEFT JOIN`이므로 수강생이 한 명도 없는 강의도 결과에 포함된다(0/0 방어 필요).

## 엣지 케이스 및 한계

- **기존 Enrollment 행의 가격 스냅샷 부재**: `V3` 마이그레이션으로 `price` 컬럼을 추가할 때, 001에서 이미 생성된 기존 `enrollment` 행에는 신청 시점 가격 정보가 없다. 이 프로젝트는 로컬 개발 데이터만 존재하므로(운영 데이터 없음), 마이그레이션 시 `price` 기본값을 현재 `course.price`로 백필하거나 0으로 두는 두 선택지가 있다 — plan.md에서 확정한다.
- **완료율 0/0 처리**: 수강생이 전혀 없는 강의(`student_count = 0`)는 완료율 계산 시 0으로 나누기가 발생한다. `student_count = 0`이면 완료율을 0(또는 null)로 처리해야 한다.
- **동시 완료 처리 요청**: 동일 Enrollment에 대해 완료 처리가 동시에 두 번 요청되면(레이스), `Enrollment.complete()`의 상태 전이 가드(이미 COMPLETED면 예외)가 애플리케이션 레벨에서 걸러낸다. 001의 `(course_id, user_id)` DB 유니크 제약과 달리 이 경합은 DB 제약이 아닌 낙관적 갱신에 의존하므로, 극히 드문 동시 완료 요청 두 개가 모두 성공(둘 다 이미 COMPLETED가 아닌 상태에서 읽어 통과)할 이론적 여지가 있다 — 001의 신청 정합성만큼 엄격한 보장은 이번 spec의 범위가 아니라고 판단한다(완료 처리는 강사 1인의 관리 행위라 동시 요청 가능성이 신청 API보다 현저히 낮다).
- **강사 권한 검증 위치**: 001 CHANGES.md에 기록된 대로 `PublishCourseUseCase`/`CloseCourseUseCase`는 강사 소유권 검증이 없다(SC 부재로 미구현). 이번 spec은 FR-003/FR-005로 소유권 검증을 명시적 요구사항으로 정의했으므로, `statistics`/`enrollment` 유스케이스에서는 반드시 `course.instructorId == requesterId` 검증을 구현한다 — 001의 기존 갭을 이번 spec에서 답습하지 않는다.
