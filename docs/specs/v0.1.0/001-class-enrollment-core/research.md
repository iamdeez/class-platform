# Research: class-enrollment-core

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

그린필드 프로젝트다. `/Users/krystal/workspace/class-platform`에는 본 spec 작성 시점까지 소스 코드가 존재하지 않는다 (`docs/` 폴더만 생성됨). 따라서 클래스 계층 구조·영향 파일 분석은 해당 없음이며, 아래 "기술 선택 조사"에서 신규 프로젝트 구조를 결정한다.

## 기술 선택 조사

### 언어·프레임워크

- **Kotlin + Spring Boot**: 채용공고 기술스택(Java17/Kotlin, Spring Boot)에 맞춘다. Kotlin의 `data class`(값 객체), `sealed class`/`enum class`(상태 모델링), non-null 타입 시스템을 도메인 모델링에 적극 활용해 클린 코드·가독성 요구사항(자격요건)에 대응한다.

### 영속성 계층

- **JPA**: 강의(Course)·수강신청(Enrollment) 애그리거트의 영속성에 사용한다. 도메인 모델과 JPA 엔티티를 분리(레이어드 아키텍처)하여 도메인 계층이 JPA 애노테이션에 오염되지 않도록 한다 (NFR-003).
- **MyBatis**: 본 spec에서는 사용하지 않는다. 복잡한 통계·조회는 004 spec에서 별도 도입 예정이며, 조기 도입 시 범위가 커져 001의 "핵심 도메인 정착"이라는 목표가 흐려진다.
- **MySQL**: 강의·수강신청 모두 강한 정합성(중복 신청 방지, 상태 전이)이 필요한 관계형 데이터이므로 MySQL을 사용한다. MongoDB(커뮤니티 게시글용)·Redis(캐싱용)는 002/003 spec에서 도입한다.

### 동시성 정합성 설계 (NFR-002, SC-006)

동일 사용자·동일 강의 조합에 대한 중복 수강 신청을 애플리케이션 레벨의 "조회 후 삽입" 로직만으로 막으면 동시 요청 시 레이스 컨디션이 발생한다 (`01-design-rules.md §6-1` Check-Then-Act 패턴).

- **채택안**: `enrollment` 테이블에 `(course_id, user_id)` 복합 유니크 제약을 두어 DB 레벨에서 최종 정합성을 보장한다. 애플리케이션은 유니크 제약 위반(`DataIntegrityViolationException`)을 캐치하여 도메인 예외(`DuplicateEnrollmentException`)로 변환한다.
- **대안 비교**: 분산 락(Redis 락)은 003 spec에서 캐싱 인프라가 도입된 이후에나 자연스럽고, 001 시점에 조기 도입하면 불필요한 외부 의존이 생긴다. DB 유니크 제약은 별도 인프라 없이 정합성을 보장하는 가장 단순한 방법이라 001에 적합하다.

### 패키지 구조 (Bounded Context 단위)

```
com.classplatform
├── course/                  ← 강의 Bounded Context
│   ├── domain/              ← Course, CourseStatus, CourseRepository(인터페이스), 도메인 예외
│   ├── application/         ← RegisterCourseUseCase, PublishCourseUseCase, CloseCourseUseCase, ListCoursesUseCase, GetCourseUseCase
│   ├── infrastructure/      ← CourseJpaEntity, CourseJpaRepository, CourseRepositoryImpl
│   └── presentation/        ← CourseController, 요청/응답 DTO
├── enrollment/               ← 수강신청 Bounded Context
│   ├── domain/               ← Enrollment, EnrollmentRepository(인터페이스), 도메인 예외
│   ├── application/          ← EnrollUseCase, CancelEnrollmentUseCase, ListMyEnrollmentsUseCase
│   ├── infrastructure/       ← EnrollmentJpaEntity, EnrollmentJpaRepository, EnrollmentRepositoryImpl
│   └── presentation/         ← EnrollmentController, 요청/응답 DTO
└── common/                   ← UserId 값 객체, 공통 예외 핸들러(ControllerAdvice), API 응답 래퍼
```

- 도메인 엔티티명은 "강의"를 `Course`로 명명한다 (Kotlin 예약어 `class`와의 혼동을 피하기 위함).
- `enrollment` 모듈은 `course` 모듈의 `CourseRepository`(조회 전용)에 의존하되, 역방향 의존은 두지 않는다 — 강의 상태(PUBLISHED 여부) 확인이 필요하기 때문.

## 엣지 케이스 및 한계

- **강의 삭제**: 본 spec 범위에 강의 삭제 기능은 없다 (FR 미정의). 상태를 CLOSED로 전이하는 것으로 "모집 마감"을 표현하며, 물리적 삭제는 향후 필요 시 별도 FR로 추가한다.
- **가격 음수·0 처리**: FR-001의 "가격" 필드는 0 이상의 값만 허용한다 (도메인 불변식). 음수 입력 시 SC-001과 동일하게 거부된다.
- **수강 취소 후 재신청**: FR-005로 취소된 수강 신청은 삭제가 아닌 상태 변경(CANCELLED)으로 남겨 이력을 보존한다. `(course_id, user_id)` DB 유니크 제약은 CANCELLED 레코드에도 동일하게 적용되므로, **취소 후 동일 강의 재신청은 001 범위에서 지원하지 않는다** (스펙 FR에 명시되지 않은 케이스이며, MySQL은 조건부 유니크 인덱스를 지원하지 않아 "활성 상태만 유니크"를 단순 제약으로 표현할 수 없다). 재신청 지원이 필요해지면 별도 spec에서 유니크 제약을 애플리케이션 레벨 검증 + 비관적 락 조합으로 재설계한다.
- **테스트 격리**: NFR-002(동시성) 검증은 단위 테스트만으로는 불가능하며, 통합 테스트에서 다중 스레드로 동시 요청을 발생시켜 검증한다 (plan.md 테스트 전략 참조).
