# Project Context

> 이 문서는 프로젝트의 **현재 상태를 묘사**하는 살아있는 참조 문서다.
> 새로운 spec 설계 전 반드시 읽어 프로젝트 구조·흐름·용어를 숙지한다.
>
> - **갱신 시점**: spec 구현·검증 완료 후 갱신한다.
> - **작성 원칙**: 현재 코드베이스의 사실만 기록한다. 미래 계획이나 설계 의도는 spec.md에 작성한다.
> - **기준 커밋**: 이 문서는 **§7 갱신 이력의 마지막 commit 기준**이다.

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
- **현재 버전**: v0.1.0 (001, 002, 003, 004, 005, 006 spec 구현 완료)
- **주요 기술 스택 (적용됨)**: Kotlin 1.9.25, Spring Boot 3.3.4, JDK 17, Gradle(Kotlin DSL). 001: Spring Data JPA + Hibernate, Flyway, MySQL 8.0. 002: Spring Data MongoDB, MongoDB 8.0, `com.anthropic:anthropic-java:2.34.0`(Claude API, Structured Outputs), `org.jsoup:jsoup`(HTML sanitize, 001 후속 도입 후 002도 재사용). 003: Spring Data Redis(Lettuce), Redis 7(alpine) — 좋아요·조회수 캐싱. 004: `mybatis-spring-boot-starter:3.0.3` — 강사 대시보드 통계 집계(GROUP BY + `CASE WHEN` 조건부 카운트/합계 단일 쿼리, `course`/`enrollment`와 동일 MySQL `DataSource` 공유). 005: `spring-boot-starter-actuator`(헬스체크) — 도메인 코드 변경 없이 GitHub Actions CI/CD + Render/MongoDB Atlas/Upstash/Aiven 배포 구성(상세는 `infra.md` 참조). 006: Terraform(`mongodbatlas`/`upstash`/`aiven` provider) — 도메인 코드 변경 없이 005의 관리형 DB 3종을 IaC로 편입(`infra/terraform/`, 상세는 `infra.md` 참조). JUnit5 + MockK/springmockk(유닛·슬라이스), Testcontainers-MySQL/-MongoDB(통합, Redis는 공식 Testcontainers 모듈이 없어 `GenericContainer` 사용). 상세 선정 근거는 각 spec의 `research.md` 참조.

## 2. 프로젝트 구조

### 디렉토리 레이아웃

```
class-platform/
├── CLAUDE.md
├── .claude/docs/                     ← constitution.md, context.md(본 문서), infra.md
├── docs/specs/v0.1.0/                ← 001, 002, 003, 004 spec 설계 문서 + CHANGES.md + DIFF-*.md
├── docker-compose.yml                ← 로컬 MySQL + MongoDB + Redis 컨테이너
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/classplatform/
    │   ├── common/                   ← 전 도메인 공유 (UserId, ApiResponse, 예외 처리, HtmlSanitizer, AiEnrichmentConfig, SchedulingConfig)
    │   ├── course/                   ← course 애그리거트 (MySQL/JPA, 아래 레이어 구조 참조)
    │   ├── enrollment/                ← enrollment 애그리거트 (MySQL/JPA. price 스냅샷·완료 상태 포함, 004)
    │   ├── post/                     ← post 애그리거트 (MongoDB, AI 태깅 + Redis 좋아요/조회수/캐싱 포함)
    │   ├── comment/                   ← comment 애그리거트 (MongoDB)
    │   └── statistics/                ← 통계 조회 전용 bounded context (MySQL/MyBatis, 애그리거트 없음, 004)
    └── main/resources/
        ├── application.yml
        ├── db/migration/             ← Flyway 마이그레이션 (V1~V3 — course/enrollment 전용, post/comment는 MongoDB라 스키마리스)
        └── mybatis/mapper/           ← MyBatis XML 매퍼 (`mybatis.mapper-locations`로 명시 지정, 004)
```

### 레이어 구조

6개 bounded context 패키지(`course`, `enrollment`, `post`, `comment`, `statistics`) 내부는 모두 아래 4개 계층으로 구성된다 (DDD + 포트-어댑터 패턴). `domain`은 프레임워크 비의존 순수 Kotlin이다 (constitution P-001) — `post/domain`의 `AiTaggingPort`/`PostPopularityPort`/`PostCachePort`, `statistics/domain`의 `CourseStatisticsRepository` 모두 예외 없이 Claude SDK·Redis·MyBatis 타입에 의존하지 않는다. `statistics`는 애그리거트가 없는 순수 조회 전용 컨텍스트로, `domain`에는 엔티티 대신 값 객체(`CourseStatistics`)만 존재한다.

```
presentation   ← REST Controller + DTO. X-User-Id 헤더로 사용자 식별
      ↓
application    ← UseCase. 도메인 오케스트레이션 (post/comment는 MongoDB 특성상 JPA @Transactional 미사용)
      ↓
domain         ← 엔티티(불변식·상태 전이 규칙) 또는 값 객체(statistics), Repository 인터페이스(포트), 도메인 예외,
                 (post만) AiTaggingPort, PostPopularityPort, PostCachePort
      ↑
infrastructure ← JPA/Mongo Entity·Document + JpaRepository/MongoRepository + RepositoryImpl(어댑터).
                 (post만) ClaudeAiTaggingClient, PostCreatedEventListener,
                 RedisPostPopularityAdapter, RedisPostCacheAdapter, PopularityCacheSyncScheduler.
                 (statistics만) CourseStatisticsMapper(MyBatis @Mapper + XML)
```

### 핵심 모듈 목록

| 모듈 / 클래스 | 위치 | 역할 | 비고 |
|---|---|---|---|
| `Course` | `course/domain/Course.kt` | 강의 애그리거트 루트. `private constructor` + 정적 팩토리(`register`/`reconstitute`)로 생성 제한, `publish()`/`close()`로만 상태 전이 | concrete, MySQL/JPA |
| `CourseStatus` | `course/domain/CourseStatus.kt` | DRAFT→PUBLISHED→CLOSED 단방향 전이 규칙(`canTransitionTo`) | enum |
| `CourseRepository` / `CourseRepositoryImpl` | `course/domain/`, `course/infrastructure/` | 포트/JPA 어댑터. `findAllByInstructorId()`(상태 무관, DRAFT 포함, 004)로 강사 소유 강의 전체 조회 | `@Repository` |
| `CourseController` | `course/presentation/CourseController.kt` | `/api/courses` REST API (등록/목록/상세/상태변경) | 구현 완료 |
| `Enrollment` | `enrollment/domain/Enrollment.kt` | 수강신청 애그리거트 루트. `cancel()`/`complete()`로만 상태 전이(`canTransitionTo()` 전이표, 004). `price`(BigDecimal, 신청 시점 강의 가격 스냅샷, 004)는 `create()`/`reconstitute()`에 기본값 없이 필수 파라미터로 강제 | concrete, MySQL/JPA |
| `EnrollmentStatus` | `enrollment/domain/EnrollmentStatus.kt` | ACTIVE→COMPLETED / ACTIVE→CANCELLED 단방향 전이 규칙(`canTransitionTo`, 004). COMPLETED/CANCELLED 모두 종단 상태 | enum |
| `EnrollmentRepository` / `EnrollmentRepositoryImpl` | `enrollment/domain/`, `enrollment/infrastructure/` | 포트/JPA 어댑터 | `@Repository` |
| `CompleteEnrollmentUseCase` | `enrollment/application/CompleteEnrollmentUseCase.kt` | 강사가 수강을 완료 처리(404/404/403/409 순서로 검증, 004) | `@Service` |
| `ListMyEnrollmentsUseCase` | `enrollment/application/ListMyEnrollmentsUseCase.kt` | `status != CANCELLED`로 필터(004에서 `== ACTIVE` → `!= CANCELLED`로 수정 — COMPLETED도 목록에 표시되어야 하므로) | `@Service` |
| `EnrollmentController` | `enrollment/presentation/EnrollmentController.kt` | `/api/courses/{id}/enrollments`, `/api/enrollments/{id}`, `/api/users/me/enrollments`, `/api/enrollments/{id}/complete`(004) REST API | 구현 완료 |
| `CourseAccessDeniedException` | `course/domain/exception/CourseAccessDeniedException.kt` | 강의 소유권 검증 실패(403). `statistics`/`enrollment` 유스케이스가 사용(004) | `ForbiddenActionException` |
| `Post` | `post/domain/Post.kt` | 게시글 애그리거트 루트. `register()`/`reconstitute()` 팩토리, `updateContent()`/`markEnrichmentCompleted()`/`markEnrichmentFailed()`/`applyPopularitySnapshot()`로만 상태 전이. `aiStatus`(PENDING/COMPLETED/FAILED), `likeCount`/`viewCount`(003, Redis→Mongo 동기화 스냅샷) 보유. `toSnapshot()`/`fromSnapshot()`으로 `PostSnapshot`(캐시 값 객체) 변환 | concrete, MongoDB |
| `AiTaggingPort` | `post/domain/AiTaggingPort.kt` | AI 태깅 포트 인터페이스(`generateTagsAndSummary(title, body): AiEnrichmentResult`). SDK 타입 비의존 | interface |
| `PostPopularityPort` | `post/domain/PostPopularityPort.kt` | 좋아요(Set)·조회수(카운터)·랭킹(ZSet) 포트. 좋아요 수는 별도 카운터 없이 Set의 `SCARD`가 단일 소스 | interface |
| `PostCachePort` | `post/domain/PostCachePort.kt` | 게시글 상세 캐시(cache-aside) 포트. `PostSnapshot` 단위로 get/put | interface |
| `PostRepository` / `PostRepositoryImpl` | `post/domain/`, `post/infrastructure/` | 포트/MongoDB 어댑터. `save()`가 문서 전체를 덮어쓰므로 수정 시 기존 `createdAt`을 조회해 보존. `findAllByIds()`로 인기 목록 배치 조회(N+1 방지) | `@Repository` |
| `ClaudeAiTaggingClient` | `post/infrastructure/ClaudeAiTaggingClient.kt` | `AiTaggingPort` 구현체. `anthropic-java` SDK Structured Outputs로 Claude API 호출, `AnthropicException` 전체를 `AiTaggingFailedException`으로 변환 | `@Component` |
| `RedisPostPopularityAdapter` | `post/infrastructure/RedisPostPopularityAdapter.kt` | `PostPopularityPort` 구현체. 좋아요는 `SADD`/`SREM`/`SCARD`, 조회수는 `INCR`, 랭킹은 `ZADD`(절대값 재기록으로 자연 치유) | `@Component` |
| `RedisPostCacheAdapter` | `post/infrastructure/RedisPostCacheAdapter.kt` | `PostCachePort` 구현체. Jackson으로 `PostSnapshot` 직렬화 후 TTL과 함께 저장 | `@Component` |
| `PopularityCacheSyncScheduler` | `post/infrastructure/PopularityCacheSyncScheduler.kt` | `@Scheduled`(`popularity-cache.sync-interval-ms` 주기, `initialDelay` 동일) 주기 배치. Redis dirty set을 소비해 `Post.applyPopularitySnapshot()` 후 저장. 개별 게시글 실패는 `runCatching`으로 격리 | `@Component` |
| `PostCreatedEventListener` | `post/infrastructure/PostCreatedEventListener.kt` | `@Async` + `@EventListener`로 `PostCreatedEvent` 수신 후 `EnrichPostUseCase` 호출 | `@Component` |
| `CreatePostUseCase` 등 6종 | `post/application/` | 게시글 CRUD + `EnrichPostUseCase`(AI 태깅 성공/실패를 폭넓게 흡수해 FAILED로 전이) | `@Service` |
| `LikePostUseCase`/`UnlikePostUseCase` | `post/application/` | 좋아요 토글. `addLike`/`removeLike` 반환값으로 분기하지 않고 항상 `getLikeCount()`로 재조회해 응답(멱등) | `@Service` |
| `GetPostUseCase` | `post/application/GetPostUseCase.kt` | 게시글 상세 조회. `PostDetail(post, likeCount, viewCount)` 반환. 캐시 우선 조회(cache-aside) + 조회수 증가(실패 무시) + 실시간 좋아요/조회수 조회(실패 시 저장된 스냅샷 폴백, NFR-002) | `@Service` |
| `ListPopularPostsUseCase` | `post/application/ListPopularPostsUseCase.kt` | Redis 랭킹(top 10) → `findAllByIds()` 배치 조회로 인기 게시글 목록 구성 | `@Service` |
| `PostController` | `post/presentation/PostController.kt` | `/api/posts` REST API — CRUD 5종 + 좋아요/취소(`/{postId}/likes`) + 인기 목록(`/popular`) | 구현 완료 |
| `Comment` | `comment/domain/Comment.kt` | 댓글 애그리거트 루트. 수정 기능 없음(스펙 범위 외) — mutation 메서드 없는 값 객체 | concrete, MongoDB |
| `CommentRepository` / `CommentRepositoryImpl` | `comment/domain/`, `comment/infrastructure/` | 포트/MongoDB 어댑터. `findTop100ByPostIdOrderByCreatedAtAsc`로 NFR-002(최대 100건) 만족 | `@Repository` |
| `CreateCommentUseCase` 등 3종 | `comment/application/` | 댓글 작성(게시글 존재 확인)/목록/삭제(작성자 검증) | `@Service` |
| `CommentController` | `comment/presentation/CommentController.kt` | `/api/posts/{postId}/comments`, `/api/comments/{commentId}` REST API | 구현 완료 |
| `CourseStatistics` | `statistics/domain/CourseStatistics.kt` | 강의별 통계 값 객체(courseId, title, studentCount, revenue, completionRate, cancellationRate). 애그리거트 없음, 영속화 대상 아님 | data class |
| `CourseStatisticsRepository` / `CourseStatisticsRepositoryImpl` | `statistics/domain/`, `statistics/infrastructure/` | 포트/MyBatis 어댑터. 원시 카운트(`CourseStatisticsRow`) → `completionRate`/`cancellationRate` 변환은 SQL이 아닌 Kotlin에서 수행(분모 0 방어) | `@Repository` |
| `CourseStatisticsMapper` | `statistics/infrastructure/CourseStatisticsMapper.kt` (+`.xml`) | `@Mapper` — GROUP BY + `CASE WHEN` 조건부 집계 단일 쿼리(NFR-001, N+1 없음). `course`/`enrollment` LEFT JOIN, 강사 목록/단일 강의 조회가 `<sql>` fragment 공유 | `@Mapper` |
| `GetInstructorStatisticsUseCase` / `GetCourseStatisticsUseCase` | `statistics/application/` | 강사 소유 강의 전체/단일 통계 조회. 단일 조회는 `courseRepository.findById()`로 소유권 먼저 검증(403/404) | `@Service` |
| `StatisticsController` | `statistics/presentation/StatisticsController.kt` | `/api/instructors/me/statistics`, `/api/courses/{courseId}/statistics` REST API | 구현 완료 |
| `UserId` | `common/UserId.kt` | `@JvmInline value class`, 양수 검증 | 전 도메인 공유 |
| `ApiResponse<T>` | `common/ApiResponse.kt` | 공통 응답 포맷(`data`/`error`) | — |
| `GlobalExceptionHandler` | `common/GlobalExceptionHandler.kt` | 도메인 예외 → HTTP 상태 코드 매핑 (`@RestControllerAdvice`) | — |
| `HtmlSanitizer` | `common/HtmlSanitizer.kt` | jsoup 기반 XSS 방어. `Course.description`(001), `Post.body`·`Comment.content`(002)가 저장 전 거친다 | object |
| `AiEnrichmentConfig` / `AiEnrichmentProperties` | `common/AiEnrichmentConfig.kt` | `@EnableAsync`, Claude SDK 클라이언트 빈, 모델 ID 설정(`ai-enrichment.model`, 기본 `claude-haiku-4-5`) | `@Configuration` |
| `MongoAuditingConfig` | `common/config/MongoAuditingConfig.kt` | `@EnableMongoAuditing`을 메인 애플리케이션 클래스가 아닌 별도 Configuration으로 분리(비-Mongo 슬라이스 테스트 오염 방지) | `@Configuration` |
| `SchedulingConfig` | `common/config/SchedulingConfig.kt` | `@EnableScheduling`. `AiEnrichmentConfig`(AI 태깅 전용)와 무관한 책임이라 별도 분리 | `@Configuration` |

## 3. 이벤트 및 데이터 흐름

### 주요 처리 흐름

```
HTTP 요청
      ↓
Controller  — X-User-Id 헤더 파싱, DTO 검증(@Valid)
      ↓
UseCase     — 도메인 규칙 오케스트레이션 (예: EnrollUseCase는 CourseRepository로 강의 상태를 먼저 확인,
              CreateCommentUseCase는 PostRepository로 게시글 존재를 먼저 확인)
      ↓
도메인 객체  — 불변식·상태 전이 검증, 위반 시 도메인 예외 throw
      ↓
Repository(포트) → RepositoryImpl(어댑터) → JpaRepository/MongoRepository → MySQL/MongoDB
      ↓
GlobalExceptionHandler — 도메인 예외를 HTTP 상태 코드로 변환 (404/409/400/403)
```

동시성 정합성(NFR-002, 001)은 애플리케이션 레벨 락이 아니라 `enrollment` 테이블의 `(course_id, user_id)` **DB 유니크 제약**으로 보장한다.

### 게시글 등록 → AI 태깅 비동기 흐름 (002)

```
CreatePostUseCase.execute() → Post 저장(aiStatus=PENDING) → 즉시 응답 반환 (NFR-001)
      ↓ ApplicationEventPublisher.publishEvent(PostCreatedEvent)
      ↓ (별도 스레드, @Async)
PostCreatedEventListener.onPostCreated()
      ↓
EnrichPostUseCase.execute() → AiTaggingPort.generateTagsAndSummary() (ClaudeAiTaggingClient → Claude API)
      ↓ 성공: Post.markEnrichmentCompleted(tags, summary)   실패: Post.markEnrichmentFailed() (NFR-004 장애 격리)
      ↓
PostRepository.save()
```

인프로세스 이벤트라 애플리케이션이 리스너 처리 중 재시작되면 이벤트가 유실되고 해당 게시글은 PENDING에 영구히 남는다 (§6 참조).

### 좋아요·조회수 캐싱 흐름 (003)

```
LikePostUseCase.execute() → PostPopularityPort.addLike()(Redis SADD, 멱등)
      ↓ getLikeCount()(SCARD)로 최종 좋아요 수 재조회 → refreshRanking()(ZADD 절대값) → markDirty()

GetPostUseCase.execute() → PostCachePort.get()(캐시 히트/미스) → 미스 시 PostRepository.findById() 후 캐시 적재
      ↓ incrementViewCount()(INCR, 실패 무시)
      ↓ getLikeCount()/getViewCount() 조회 → 실패 시 Post에 저장된 스냅샷 값으로 폴백(NFR-002)

PopularityCacheSyncScheduler(@Scheduled) → Redis post:dirty set 소비
      ↓ 각 postId의 getLikeCount()/getViewCount() → Post.applyPopularitySnapshot() → PostRepository.save()
```

좋아요 카운트는 Redis Set(`post:like:{postId}`)의 `SCARD`가 유일한 소스이며 별도 카운터를 두지 않는다(카운터-집합 drift 방지). 랭킹(`post:popular` ZSet)은 좋아요/취소 시마다 절대값으로 `ZADD`되어 매번 자연 치유된다. **한 번도 좋아요를 받지 못한 게시글은 랭킹에 아예 등록되지 않는다** — `refreshRanking()`이 호출된 적이 없기 때문(0점으로 등록되는 것이 아니라 완전 부재).

### 강사 대시보드 통계 집계 흐름 (004)

```
GetInstructorStatisticsUseCase.execute(instructorId)
      ↓
CourseStatisticsRepository.findAllByInstructorId() → CourseStatisticsMapper(MyBatis, 단일 GROUP BY 쿼리)
      ↓ course LEFT JOIN enrollment, CASE WHEN으로 상태별 카운트/합계를 한 번에 집계
CourseStatisticsRepositoryImpl에서 원시 카운트 → completionRate/cancellationRate 계산(분모 0 방어)

CompleteEnrollmentUseCase.execute(enrollmentId, requesterId)
      ↓ enrollmentRepository.findById()(404) → courseRepository.findById()(404)
      ↓ course.instructorId == requesterId 검증(403) → enrollment.complete()(전이 위반 시 409)
```

매출(`revenue`)은 `course.price`(현재값)가 아닌 `Enrollment.price`(신청 시점 스냅샷)의 합이다 — 강의 가격이 나중에 바뀌어도 과거 매출 집계는 변하지 않는다. 이 프로젝트에는 "강의 가격 변경" 유스케이스 자체가 없다(`Course.price`는 불변).

### 외부 시스템 연동

| 시스템 | 연동 방식 | 담당 모듈 | 주의사항 |
|---|---|---|---|
| Claude API (Anthropic) | `com.anthropic:anthropic-java` SDK, Structured Outputs | `post/infrastructure/ClaudeAiTaggingClient.kt` | 모델 ID는 `ai-enrichment.model` 설정값(기본 `claude-haiku-4-5`)으로 분리. 실제 API 호출은 자동화 테스트에 포함하지 않음(비용·플레이키니스 방지, `AiTaggingPort` mock으로 대체) |
| Redis | Spring Data Redis(Lettuce), `StringRedisTemplate` | `post/infrastructure/{RedisPostPopularityAdapter, RedisPostCacheAdapter, PopularityCacheSyncScheduler}.kt` | 001(MySQL)·002(MongoDB)에 이은 세 번째 데이터 저장소. 연결 실패 시 좋아요/조회수/캐시 관련 호출은 모두 `runCatching`으로 흡수되어 게시글 조회 자체는 실패하지 않는다(NFR-002) |

001 spec 범위에서는 외부 시스템 연동이 없었다. 인증 서버 연동은 아직 미도입(§6 참조).

## 4. 도메인 모델

### 핵심 엔티티 (구현 완료 기준)

| 엔티티 | 설명 | 주요 속성 | 불변식 |
|---|---|---|---|
| `Course` | 강의 애그리거트 루트 | id, title, description, price(BigDecimal), instructorId(UserId), status(DRAFT/PUBLISHED/CLOSED) | title 공백 불가, price 음수 불가, 상태는 DRAFT→PUBLISHED→CLOSED 단방향만 |
| `Enrollment` | 수강신청 애그리거트 루트 | id, courseId, userId(UserId), price(BigDecimal, 신청 시점 강의 가격 스냅샷, 004), status(ACTIVE/COMPLETED/CANCELLED) | 이미 CANCELLED/COMPLETED인 신청을 다시 전이시키면 예외(`InvalidEnrollmentStatusException`, 409). `price`는 기본값 없이 필수 |
| `CourseStatistics` | 강의별 통계 값 객체(애그리거트 아님, 영속화 안 됨, 004) | courseId, title, studentCount(ACTIVE+COMPLETED), revenue(ACTIVE+COMPLETED의 price 합), completionRate, cancellationRate | studentCount/totalCount가 0이면 completionRate/cancellationRate는 0(0 나눗셈 방어) |
| `Post` | 게시글 애그리거트 루트 | id(String, MongoDB ObjectId), title, body, authorId(UserId), aiStatus(PENDING/COMPLETED/FAILED), tags(List\<String\>, 최대 5개), summary(String?, 최대 200자), likeCount(Long, 기본 0), viewCount(Long, 기본 0) | title/body 공백 불가, tags 5개 초과·summary 200자 초과 시 `markEnrichmentCompleted()`에서 예외, likeCount/viewCount 음수 시 `applyPopularitySnapshot()`에서 예외. **likeCount/viewCount는 Redis→Mongo 주기 동기화 스냅샷이며, API 응답의 실시간 값은 `PostPopularityPort`에서 직접 조회한다(003)** |
| `Comment` | 댓글 애그리거트 루트 | id(String), postId(String), authorId(UserId), content | content 공백 불가. 수정 없음(불변) |

### 엔티티 관계

```
Course (1) ──── 수강 대상 ───→ (N) Enrollment
Post (1) ──── 댓글 대상 ───→ (N) Comment
```

`Enrollment`는 `courseId`로만 `Course`를 참조한다 (JPA 연관관계 매핑이 아닌 ID 참조 — 애그리거트 간 느슨한 결합 유지). `Comment`도 동일하게 `postId`로만 `Post`를 참조하며, MongoDB 레벨의 FK 제약은 없다(애플리케이션 레벨에서 `CreateCommentUseCase`가 `PostRepository`로 존재 여부만 확인).

## 5. 도메인 용어 사전 (Glossary)

| 용어 | 정의 | 사용 금지 동의어 |
|---|---|---|
| Course | 강의. 애그리거트 루트 엔티티명 | Class(Kotlin 예약어 `class`와 혼동 방지 위해 미사용), Lecture |
| Enrollment | 수강신청. 애그리거트 루트 엔티티명 | Registration, Subscription |
| CourseStatus | 강의 상태(DRAFT/PUBLISHED/CLOSED) | — |
| EnrollmentStatus | 수강신청 상태(ACTIVE/COMPLETED/CANCELLED, 004에서 COMPLETED 추가) | — |
| Post | 커뮤니티 게시글. 애그리거트 루트 엔티티명 | Article, Board |
| Comment | 게시글에 달린 댓글. 애그리거트 루트 엔티티명. 대댓글(중첩) 없음 | Reply |
| PostAiStatus | 게시글 AI 처리 상태(PENDING/COMPLETED/FAILED) | — |
| AiTaggingPort | AI 태깅(카테고리 태그·요약 생성) 도메인 포트 인터페이스 | — |
| PostPopularityPort | 좋아요·조회수·인기 랭킹 캐시 도메인 포트 인터페이스 | — |
| PostCachePort | 게시글 상세 캐시(cache-aside) 도메인 포트 인터페이스 | — |
| PostSnapshot | 캐시·동기화용 게시글 스냅샷 값 객체(Post의 부분 표현) | — |
| CourseStatistics | 강의별 수강생 수·매출·완료율·취소율 통계 값 객체 | — |
| UserId | 사용자 식별자 값 객체. `common` 패키지 소속 (모든 bounded context 공유) | — |

## 6. 알려진 제약 및 기술 부채

| 항목 | 내용 | 영향 범위 | 관련 spec |
|---|---|---|---|
| 취소 후 재신청 미지원 | `enrollment` 테이블의 `(course_id, user_id)` 유니크 제약이 CANCELLED 레코드에도 적용되어, 취소한 강의에 동일 사용자가 재신청 불가 (MySQL이 조건부 유니크 인덱스를 지원하지 않아 발생한 설계 트레이드오프) | `enrollment` 도메인 | `docs/specs/v0.1.0/001-class-enrollment-core/research.md` |
| 인증·인가 미구현 | 사용자 식별은 `X-User-Id` 헤더를 신뢰하는 방식으로만 처리되며 실제 인증 체계 없음 | 전체 API | 001/002 spec.md 모두 범위 외로 명시 |
| Course 소유자 권한 체크 미구현 | `PublishCourseUseCase`/`CloseCourseUseCase`는 강사 소유자 검증이 없다(001 CHANGES.md 기록). `Post`/`Comment`는 반대로 수정·삭제 시 소유자 검증(`PostAccessDeniedException`/`CommentAccessDeniedException`)이 구현되어 있어 도메인 간 정책이 비대칭이다. 004에서 신설한 `CourseAccessDeniedException`(`statistics`/`CompleteEnrollmentUseCase`가 사용)을 재사용하면 해소 가능하나, 이 두 유스케이스에는 여전히 SC가 없어 범위 밖 | `course` 애플리케이션 | `docs/specs/v0.1.0/CHANGES.md` |
| AI 처리 실패 후 재시도 미지원 | `Post.aiStatus`가 FAILED로 전이된 후 자동·수동 재시도 로직이 없다 (spec.md 범위 외로 명시) | `post` 도메인 | `docs/specs/v0.1.0/002-community-post-ai-tagging/spec.md` |
| 인프로세스 비동기 이벤트 유실 가능성 | `PostCreatedEvent`는 스프링 인프로세스 이벤트라, 리스너가 AI 호출을 기다리는 도중 애플리케이션이 재시작되면 이벤트가 유실되고 해당 게시글은 PENDING에 영구히 남는다. 메시지 브로커 도입 전까지 감수하는 한계 | `post` 도메인 | `docs/specs/v0.1.0/002-community-post-ai-tagging/research.md` |
| Comment 목록 페이지네이션 부재 | NFR-002가 댓글 목록도 페이지네이션 지원을 요구하지만 `GET /api/posts/{postId}/comments`에는 `page`/`size` 쿼리 파라미터가 없다. 서버 내부에서 `findTop100ByPostIdOrderByCreatedAtAsc`로 최대 100건 상한만 둠 | `comment` 도메인 | `docs/specs/v0.1.0/002-community-post-ai-tagging/tasks.md` T011 |
| 좋아요 0건 게시글은 인기 랭킹에서 완전히 부재 | `post:popular` ZSet은 `refreshRanking()`이 호출된 적 있는(최소 1회 좋아요/취소) 게시글만 담는다. "0점으로 노출"이 아니라 항목 자체가 없다 | `post` 도메인 | `docs/specs/v0.1.0/003-like-view-count-caching/tasks.md` T014 |
| Redis 동기화 배치의 유실 가능성 | `PopularityCacheSyncScheduler`가 Redis dirty set을 먼저 소비(제거)한 뒤 처리하므로, 처리 도중 예외가 나면 해당 게시글은 다음 좋아요/취소가 다시 `markDirty()`할 때까지 MongoDB 동기화가 미뤄질 수 있다(002의 인프로세스 이벤트 유실과 같은 계열) | `post` 도메인 | `docs/specs/v0.1.0/003-like-view-count-caching/tasks.md` T009 |
| 조회수 어뷰징 방지 미도입 | 동일 사용자·세션의 짧은 시간 내 중복 조회를 걸러내는 로직이 없다(spec.md 범위 외로 명시) | `post` 도메인 | `docs/specs/v0.1.0/003-like-view-count-caching/spec.md` |
| 완료 처리 취소(되돌리기) 미지원 | 수강 완료는 단방향 처리만 지원한다(`COMPLETED`에서 되돌리는 API 없음). 진도율 기반 자동 완료 판정도 없음(강사 수동 처리만) | `enrollment` 도메인 | `docs/specs/v0.1.0/004-complex-query-statistics/spec.md` |
| 강의 가격 변경 유스케이스 없음 | `Course.price`는 `register()` 이후 변경 불가(불변). 매출 통계는 `Enrollment.price` 스냅샷 기반이라 이 제약과 무관하게 동작하지만, "강의 가격 수정" 자체가 필요해지면 별도 spec에서 다뤄야 한다 | `course` 도메인 | `docs/specs/v0.1.0/004-complex-query-statistics/research.md` |

## 7. 갱신 이력

| 날짜 | commit | 갱신 내용 | 관련 spec |
|---|---|---|---|
| 2026-07-09 | (커밋 전) | 최초 작성 (그린필드, 소스 코드 없음) | — |
| 2026-07-09 | `44f3192` | 001 spec 진행 중간 상태 반영 — Course·Enrollment 도메인/애플리케이션/인프라 구현 완료, 프로젝트 구조·레이어·핵심 모듈·데이터 흐름·도메인 모델을 실제 코드 기준으로 갱신. EnrollmentController 및 일부 테스트(T013~T017) 미완료 상태를 기술 부채로 기록 | `docs/specs/v0.1.0/001-class-enrollment-core/` |
| 2026-07-12 | `6df6a9d` | 001 완료(EnrollmentController 구현, SC-001~009 테스트 전체 완료, HtmlSanitizer 후속 보완) + 002 spec(post/comment 애그리거트, MongoDB 도입, Claude API 비동기 AI 태깅) 구현 완료를 한 번에 반영. 프로젝트 구조·레이어·핵심 모듈·이벤트 흐름(비동기 AI 태깅 추가)·도메인 모델·용어 사전·기술 부채를 실제 코드 기준으로 전면 갱신 | `docs/specs/v0.1.0/001-class-enrollment-core/`, `docs/specs/v0.1.0/002-community-post-ai-tagging/` |
| 2026-07-12 | `1f36131`(+Phase 4 미커밋분) | 003 spec(Redis 도입, 게시글 좋아요·조회수·인기 랭킹·상세 캐시) 구현 완료 반영. `PostPopularityPort`/`PostCachePort`/`PostSnapshot`/`SchedulingConfig` 등 신규 모듈, 좋아요·조회수 캐싱 흐름, `Post` 엔티티 필드 확장, 용어 사전, 기술 부채(좋아요 0건 게시글 랭킹 부재, Redis 동기화 유실 가능성)를 실제 코드 기준으로 갱신 | `docs/specs/v0.1.0/003-like-view-count-caching/` |
| 2026-07-12 | `2990580` | 004 spec(MyBatis 도입, 강사 대시보드 통계, Enrollment 완료 상태·가격 스냅샷) 구현 완료 반영. `statistics` bounded context 신설(6번째), `Enrollment`/`EnrollmentStatus` 확장, `CourseAccessDeniedException` 신설, 통계 집계 흐름, 도메인 모델(`Enrollment.price`, `CourseStatistics`), 용어 사전, 기술 부채(완료 처리 취소 미지원, 강의 가격 변경 유스케이스 없음, "MyBatis 미도입" 항목 해소로 제거)를 실제 코드 기준으로 갱신 | `docs/specs/v0.1.0/004-complex-query-statistics/` |
| 2026-07-13 | `c63bafe` | 005 spec(GitHub Actions CI/CD, Render/MongoDB Atlas/Upstash/Aiven 배포) 완료 반영. 도메인 코드 변경은 없음(순수 인프라 spec) — `spring-boot-starter-actuator` 추가, 프로젝트 개요의 기술 스택·버전 표기만 갱신. 배포 구성 상세는 `infra.md` 참조 | `docs/specs/v0.1.0/005-ci-cd-deploy/` |
| 2026-07-13 | `3675f8d` | 006 spec(Terraform으로 Atlas/Upstash/Aiven 편입) 완료 반영. 도메인 코드 변경 없음(순수 인프라 spec) — 프로젝트 개요의 기술 스택·버전 표기만 갱신. IaC 구성 상세는 `infra.md` 참조 | `docs/specs/v0.1.0/006-infrastructure-as-code/` |
