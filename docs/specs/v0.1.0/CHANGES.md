# Changes: v0.1.0

## [004-complex-query-statistics] 구현 완료

**변경 파일**:

인프라/설정:
- `build.gradle.kts`: `mybatis-spring-boot-starter:3.0.3` 추가(001의 JPA, 002의 MongoDB, 003의 Redis에 이어 네 번째 영속성 기술. `course`/`enrollment` 모두 MySQL 소속이라 동일 `DataSource`를 JPA와 함께 공유하며 별도 트랜잭션 매니저 조정이 불필요)
- `src/main/resources/application.yml`: `mybatis.mapper-locations`(`classpath:mybatis/mapper/*.xml`), `mybatis.configuration.map-underscore-to-camel-case: true` 추가
- `src/main/resources/mybatis/mapper/CourseStatisticsMapper.xml` (신규): GROUP BY + `CASE WHEN` 조건부 집계 SQL(강사 목록/단일 강의 조회가 `<sql>` fragment 공유)
- `src/main/resources/db/migration/V3__add_enrollment_price_and_completed_status.sql` (신규): `enrollment.price DECIMAL(10,2)` 추가 + 기존 행 `course.price`로 백필. `status`는 기존 VARCHAR(20) 컬럼 재사용(스키마 변경 없이 `COMPLETED` 값 허용)

Enrollment 확장(`enrollment/*`):
- `domain/EnrollmentStatus.kt`: `COMPLETED` 추가, `CourseStatus.canTransitionTo()`와 동일한 패턴으로 전이표 캡슐화(`ACTIVE→COMPLETED`/`ACTIVE→CANCELLED`만 허용, 둘 다 종단 상태)
- `domain/Enrollment.kt`: `price: BigDecimal` 필드 추가(`create()`/`reconstitute()` 시그니처 변경 — Post의 `likeCount`/`viewCount`와 달리 기본값 없이 모든 호출부에 명시를 강제하는 Breaking Change), `complete()` 메서드 추가
- `application/EnrollUseCase.kt`: `course.price`를 `Enrollment.create()`에 스냅샷으로 전달
- `application/CompleteEnrollmentUseCase.kt` (신규): 신청 없음(404) → 강의 없음(404) → 비담당 강사(403) → 상태 전이 위반(409, `InvalidEnrollmentStatusException`) → 저장
- `application/ListMyEnrollmentsUseCase.kt`: **버그 수정** — 필터 조건을 `status == ACTIVE`에서 `status != CANCELLED`로 변경(001 당시 CANCELLED만 제외할 의도였으나, 이번 spec에서 COMPLETED가 추가되며 "내 신청 목록"에서 완료된 수강까지 함께 가려지던 문제)
- `infrastructure/{EnrollmentJpaEntity, EnrollmentRepositoryImpl}.kt`: `price` 컬럼 매핑 추가
- `presentation/EnrollmentController.kt`, `presentation/dto/EnrollmentDtos.kt`: `POST /api/enrollments/{enrollmentId}/complete` 추가(`CompleteEnrollmentResponse(enrollmentId, status)`)

Course 확장(`course/*`):
- `domain/CourseRepository.kt`, `infrastructure/{CourseJpaRepository, CourseRepositoryImpl}.kt`: `findAllByInstructorId(instructorId)` 추가(상태 무관, DRAFT 포함 — 강사 본인 대시보드이므로 공개 여부와 무관하게 전부 조회)
- `domain/exception/CourseAccessDeniedException.kt` (신규): `ForbiddenActionException` 상속(403). 강의 소유권 검증 실패 시 사용(001의 `PublishCourseUseCase`/`CloseCourseUseCase`에는 여전히 이 검증이 없다 — 003 CHANGES.md에서부터 이어진 기존 갭)

Statistics 도메인 신설(`statistics/*`):
- `domain/CourseStatistics.kt` (신규): 프레임워크 비의존 값 객체(courseId, title, studentCount, revenue, completionRate, cancellationRate)
- `domain/CourseStatisticsRepository.kt` (신규): 포트 인터페이스(`findAllByInstructorId`, `findByCourseId`)
- `infrastructure/CourseStatisticsMapper.kt` (신규): `@Mapper` 인터페이스 + 원시 집계 결과 `CourseStatisticsRow`. `@SpringBootApplication`(`com.classplatform`) 하위 패키지라 별도 `@MapperScan` 없이 자동 스캔됨
- `infrastructure/CourseStatisticsRepositoryImpl.kt` (신규): 원시 카운트 → `completionRate`(`completed/(active+completed)`)·`cancellationRate`(`cancelled/total`) 변환, 분모 0 방어
- `application/{GetInstructorStatistics, GetCourseStatistics}UseCase.kt` (신규): 단일 강의 조회는 소유권 검증(403/404) 후 통계 반환
- `presentation/StatisticsController.kt`, `presentation/dto/StatisticsDtos.kt` (신규): `GET /api/instructors/me/statistics`, `GET /api/courses/{courseId}/statistics`

테스트:
- 단위: `CompleteEnrollmentUseCaseTest`(정상/404/403/409×2), `EnrollUseCaseTest`(가격 스냅샷 케이스 추가), `ListMyEnrollmentsUseCaseTest`(완료된 신청 포함 케이스 추가), `{GetInstructorStatistics, GetCourseStatistics}UseCaseTest`, `CourseStatisticsRepositoryImplTest`(SC-009/010, MockK로 Mapper 대체)
- 슬라이스(`@WebMvcTest`): `EnrollmentControllerTest`(완료 처리 케이스 추가), `StatisticsControllerTest`
- 통합(Testcontainers MySQL, `@SpringBootTest` 풀 컨텍스트): `CourseRepositoryImplIT`(강사별 목록), `EnrollmentRepositoryImplIT`(price 왕복), `EnrollmentCompletionIT`(SC-004~006), `CourseStatisticsRepositoryImplIT`(혼합 상태 집계, 0/0 방어, SC-007/008), `StatisticsControllerIT`(SC-001~003)

**설계 근거**:
- **MyBatis 도입**: `CASE WHEN` 기반 조건부 집계(활성/완료/취소별 카운트·합계를 단일 GROUP BY로)는 JPA 네이티브 쿼리보다 MyBatis XML 매퍼의 동적 SQL·원시 SQL 제어력이 더 적합하다고 판단했다(001 spec.md에서 이미 "004는 MyBatis"로 예고됨). `@DataJpaTest`(JPA 슬라이스)는 MyBatis 자동 구성을 포함하지 않아, MyBatis 관련 통합 테스트는 `@SpringBootTest` 풀 컨텍스트로 작성했다.
- **완료율/취소율 계산 위치**: SQL에서 나눗셈까지 처리하지 않고 `CourseStatisticsRepositoryImpl`(Kotlin)에서 원시 카운트로 계산한다 — DB 방언별 정수 나눗셈 함정을 피하고 0 나눗셈 방어 로직 테스트도 더 쉽다.
- **`price` 필드는 기본값 없는 Breaking Change로 설계**: 매출 계산의 핵심 도메인 데이터이므로, 필드 누락 시 조용히 0으로 채워지는 것을 방지하기 위해 모든 생성 경로(`create()`/`reconstitute()`)에서 명시적으로 값을 전달하도록 강제했다(Post의 `likeCount`/`viewCount`가 기본값 0을 허용한 것과 대비되는 결정).
- **가격 변경 시뮬레이션**: 이 프로젝트에는 "강의 가격 변경" 유스케이스 자체가 없어(`Course.price`는 불변), SC-007 검증은 서로 다른 `Enrollment.price` 스냅샷을 직접 생성해 매출이 `course.price`(현재값)가 아닌 신청 시점 스냅샷 합임을 확인하는 방식으로 작성했다.

**후속 작업 시 주의사항**:
- **`ListMyEnrollmentsUseCase` 필터 변경**: `status == ACTIVE` → `status != CANCELLED`로 바뀌었으므로, 향후 세 번째 이상의 종단 상태가 추가되면 "내 신청 목록"에 포함할지 여부를 이 필터 기준으로 다시 검토해야 한다.
- **Course 소유자 권한 체크 미구현 갭 잔존**: `PublishCourseUseCase`/`CloseCourseUseCase`는 여전히 소유권 검증이 없다(001부터 이어진 기존 갭, 이번 spec에서 신설한 `CourseAccessDeniedException`을 재사용해 해소 가능하나 SC 부재로 범위 밖).
- **완료 처리 취소(되돌리기) 미지원**: spec.md에서 범위 외로 명시한 대로 완료는 단방향 처리만 지원한다.
- **테스트에서 한글 응답 본문을 `ObjectMapper`로 직접 파싱할 때는 `MockHttpServletResponse.getContentAsString(Charsets.UTF_8)`로 charset을 명시할 것**: 미지정 시 ISO-8859-1로 디코딩되어 mojibake가 발생한다(`jsonPath()` assertion은 자체적으로 올바르게 처리하므로 영향 없음). `StatisticsControllerIT` 작성 중 처음 발견됨.
- **MySQL·MongoDB·Redis·MyBatis 4개 영속성 기술 공존**: `@SpringBootTest` 풀 컨텍스트 테스트는 이제 최대 3개의 Testcontainers(MySQL/MongoDB/Redis)를 함께 띄우며, MyBatis는 MySQL의 동일 `DataSource`를 JPA와 공유한다.

## [003-like-view-count-caching] 구현 완료

**변경 파일**:

인프라/설정:
- `docker-compose.yml`, `.env.example`: 로컬 Redis 7(alpine) 컨테이너(001의 MySQL, 002의 MongoDB와 병행)
- `build.gradle.kts`: `spring-boot-starter-data-redis` 추가(Testcontainers Redis는 공식 모듈이 없으나 `mysql`/`mongodb` 모듈이 core `GenericContainer`를 전이 의존성으로 이미 가져와 별도 추가 불필요)
- `src/main/resources/application.yml`: `spring.data.redis.{host,port}`, `popularity-cache.sync-interval-ms`(기본 10000), `post-detail-cache.ttl-seconds`(기본 300) 설정
- `common/config/SchedulingConfig.kt` (신규): `@EnableScheduling`을 AI 태깅 전용 `AiEnrichmentConfig`와 분리한 별도 Configuration

Post 도메인 확장(`post/*`):
- `domain/Post.kt`: `likeCount`/`viewCount` 필드 추가(기본 0), `applyPopularitySnapshot()`(음수 방지), `toSnapshot()`/`fromSnapshot()`(캐시 변환). `reconstitute()`는 기본값 파라미터로 확장해 기존 8개 호출부 하위 호환 유지
- `domain/{PostPopularityPort, PostCachePort, PostRanking, PostSnapshot}.kt` (신규): SDK/Redis 타입에 의존하지 않는 도메인 포트·값 객체
- `domain/PostRepository.kt`: `findAllByIds(ids)` 추가(인기 목록 배치 조회, N+1 방지)
- `infrastructure/RedisPostPopularityAdapter.kt` (신규): 좋아요는 Set의 `SCARD`를 카운트 단일 소스로 삼고(카운터 이중 관리로 인한 drift 방지), 랭킹은 `ZADD` 절대값 재기록(자연 치유)으로 관리
- `infrastructure/RedisPostCacheAdapter.kt` (신규): 게시글 상세 캐시(cache-aside, Jackson 직렬화 + TTL)
- `infrastructure/PopularityCacheSyncScheduler.kt` (신규): `@Scheduled` 주기 배치로 Redis dirty set을 MongoDB에 동기화. 개별 게시글 실패가 배치 전체를 막지 않도록 `runCatching`으로 격리
- `application/{LikePost, UnlikePost, ListPopularPosts}UseCase.kt` (신규), `application/{LikeResult, PopularPost, PostDetail}.kt` (신규 값 객체)
- `application/GetPostUseCase.kt`: 반환 타입을 `Post`에서 `PostDetail(post, likeCount, viewCount)`로 변경. 캐시 조회(cache-aside) → 조회수 증가(실패 무시) → 실시간 좋아요/조회수 조회(실패 시 저장된 스냅샷으로 폴백)
- `presentation/PostController.kt`: `POST/DELETE /api/posts/{postId}/likes`, `GET /api/posts/popular` 추가. `PostResponse`에 `likeCount`/`viewCount` 필드 추가(기존 필드 유지)

테스트:
- 단위: `PostTest`(스냅샷 검증 4건 추가), `RedisPostPopularityAdapterTest`, `RedisPostCacheAdapterTest`, `{Like,Unlike}PostUseCaseTest`, `ListPopularPostsUseCaseTest`, `PopularityCacheSyncSchedulerTest`, `GetPostUseCaseTest`(캐시·폴백·SC-009 케이스 추가)
- 슬라이스(`@WebMvcTest`): `PostControllerTest`(좋아요/취소/인기목록 케이스 추가)
- 통합(Testcontainers MySQL+MongoDB+Redis): `PostRepositoryImplIT`(스냅샷·배치조회), `PostLikeIT`(SC-001/002), `PostLikeConcurrencyIT`(SC-008, 100 스레드 동시 좋아요), `PostControllerIT`(003-SC-003/004/005 추가), `PostPopularIT`(SC-006), `PostPopularityFailureIT`(SC-007, Redis 연결 불가 상황 재현)

**후속 작업 시 주의사항**:
- **Redis 도입 완료**: 001(MySQL)·002(MongoDB)에 이어 003부터 Redis가 세 번째 데이터 저장소로 추가되어, `@SpringBootTest` 풀 컨텍스트 통합 테스트는 이제 최대 3개의 Testcontainers(MySQL/MongoDB/Redis)를 함께 띄운다.
- **좋아요 랭킹은 좋아요 0건 게시글을 포함하지 않는다**: `post:popular` ZSET은 `refreshRanking()`이 호출된 적 있는(즉 최소 1회 좋아요/취소를 받은) 게시글만 담는다. 인기 목록 API가 "좋아요 0건 게시글도 0점으로 노출"해야 하는 요구가 생기면 랭킹 갱신 로직을 게시글 생성 시점까지 확장해야 한다(현재는 spec 범위 밖으로 판단해 다루지 않음).
- **인프로세스 동기화 배치의 유실 가능성**: `PopularityCacheSyncScheduler`는 Redis `post:dirty` set을 먼저 소비(제거)한 뒤 처리하므로, 처리 도중 예외가 나면 해당 게시글은 다음 좋아요/취소가 다시 `markDirty()`할 때까지 MongoDB 동기화가 미뤄질 수 있다. 002의 "인프로세스 비동기 이벤트 유실 가능성"과 같은 계열의 한계로 감수했다.
- **테스트에서 Redis 연결 실패를 강제할 때는 `spring.data.redis.port`를 1(또는 다른 미사용 포트)로 오버라이드하는 방식이 `@MockkBean`으로 포트를 교체하는 방식보다 실제 어댑터 구현체를 그대로 통과시켜 더 faithful하다**(`PostPopularityFailureIT` 참조). 다만 `@Scheduled` 빈이 있는 컨텍스트에서 이 방식을 쓸 때는 스케줄 주기를 충분히 늦추지 않으면 백그라운드에서 커맨드 타임아웃 대기가 걸려 테스트 스위트 전체가 느려질 수 있다 — `PopularityCacheSyncScheduler`에 `initialDelayString`을 반드시 설정할 것(원래 `fixedDelayString`만 있으면 컨텍스트 기동 즉시 1회 실행된다).
- **AI 처리 실패 후 재시도 미지원(002), 댓글 목록 페이지네이션 부재(002), Course 소유자 권한 체크 미구현(001)**: 여전히 해소되지 않은 채로 남아 있다.
- **조회수 어뷰징 방지 미도입**: spec.md에서 범위 외로 명시한 대로, 동일 사용자의 짧은 시간 내 중복 조회를 걸러내는 로직이 없다.

## [002-community-post-ai-tagging] 구현 완료

**변경 파일**:

인프라/설정:
- `docker-compose.yml`, `.env.example`: 로컬 MongoDB 8 컨테이너(001의 MySQL과 병행)
- `build.gradle.kts`: `spring-boot-starter-data-mongodb`, `com.anthropic:anthropic-java:2.34.0`, `org.testcontainers:mongodb:1.20.1`(test) 추가
- `src/main/resources/application.yml`: `spring.data.mongodb.uri`, `ai-enrichment.model`(기본값 `claude-haiku-4-5`) 설정
- `common/AiEnrichmentConfig.kt` (신규): `@EnableAsync`, Claude SDK 클라이언트 빈(`AnthropicOkHttpClient.fromEnv()`), `AiEnrichmentProperties`
- `common/config/MongoAuditingConfig.kt` (신규): `@EnableMongoAuditing`을 메인 애플리케이션 클래스가 아닌 별도 `@Configuration`으로 분리(비-Mongo 슬라이스 테스트 오염 방지)

Post 도메인(`post/*`):
- `domain/Post.kt`: 애그리거트 루트. `register()`/`reconstitute()` 팩토리, title/body 불변식, `updateContent()`/`markEnrichmentCompleted()`/`markEnrichmentFailed()`로만 상태 전이. `aiStatus`(PENDING/COMPLETED/FAILED), `tags`(최대 5개), `summary`(최대 200자)
- `domain/PostAiStatus.kt`, `domain/PostRepository.kt`, `domain/AiTaggingPort.kt`(+`AiEnrichmentResult`): 프레임워크·SDK 비의존 포트 인터페이스
- `domain/event/PostCreatedEvent.kt`, `domain/exception/{PostNotFoundException, PostAccessDeniedException, AiTaggingFailedException}.kt`
- `infrastructure/{PostMongoDocument, PostMongoRepository, PostRepositoryImpl}.kt`: MongoDB 어댑터. `save()`가 문서 전체를 덮어쓰는 특성 때문에 수정 시 기존 `createdAt`을 조회해 보존
- `infrastructure/ClaudeAiTaggingClient.kt`: `StructuredMessageCreateParams`로 구조화된 출력(`tags`, `summary`)을 받아오는 어댑터. `AnthropicException` 계열 전체를 `AiTaggingFailedException`으로 변환
- `infrastructure/PostCreatedEventListener.kt`: `@Async` + `@EventListener`로 `EnrichPostUseCase` 호출
- `application/{CreatePost, GetPost, ListPosts, UpdatePost, DeletePost, EnrichPost}UseCase.kt`: `CreatePostUseCase`는 저장 직후 `PostCreatedEvent` 발행(NFR-001 비동기 처리), `UpdatePostUseCase`는 `title`/`body`를 nullable로 받아 부분 수정을 지원, `EnrichPostUseCase`는 `AiTaggingFailedException`뿐 아니라 도메인 불변식 위반까지 폭넓게 흡수해 FAILED로 전이(NFR-004 장애 격리)
- `presentation/PostController.kt`, `presentation/dto/PostDtos.kt`: REST API 5종 (`POST/GET /api/posts`, `GET/PATCH/DELETE /api/posts/{postId}`)

Comment 도메인(`comment/*`):
- `domain/Comment.kt`: 수정 기능이 없어 mutation 메서드 없는 값 객체로 설계(`write()`/`reconstitute()`)
- `domain/CommentRepository.kt`, `domain/exception/{CommentNotFoundException, CommentAccessDeniedException}.kt`
- `infrastructure/{CommentMongoDocument, CommentMongoRepository, CommentRepositoryImpl}.kt`: `findTop100ByPostIdOrderByCreatedAtAsc` 파생 쿼리로 NFR-002(최대 100건)를 별도 페이지네이션 파라미터 없이 만족
- `application/{CreateComment, ListComments, DeleteComment}UseCase.kt`: `CreateCommentUseCase`는 `PostRepository`로 게시글 존재 여부 확인, `content`는 `HtmlSanitizer`로 sanitize
- `presentation/CommentController.kt`, `presentation/dto/CommentDtos.kt`: REST API 3종 (`POST/GET /api/posts/{postId}/comments`, `DELETE /api/comments/{commentId}`)

테스트:
- 단위: `PostTest`(SC-001), `ClaudeAiTaggingClientTest`, `{Create,Get,List,Update,Delete,Enrich}PostUseCaseTest`(SC-007/008/009 포함), `{Create,List,Delete}CommentUseCaseTest`
- 슬라이스(`@WebMvcTest`): `PostControllerTest`, `CommentControllerTest`
- 통합(Testcontainers MongoDB, 일부는 MySQL도 병행): `PostRepositoryImplIT`, `CommentRepositoryImplIT`, `PostCreatedEventIT`(NFR-001 비동기 검증), `PostControllerIT`(SC-002/003/010), `PostAuthorizationIT`(SC-004), `CommentControllerIT`(SC-005/006/011)

**후속 작업 시 주의사항**:
- **MongoDB 도입 완료**: 001은 MySQL 단일 의존이었으나 002부터 MySQL(Course/Enrollment)과 MongoDB(Post/Comment)가 같은 애플리케이션 컨텍스트에 공존한다. `@SpringBootTest` 풀 컨텍스트 통합 테스트는 두 Testcontainers를 모두 띄워야 한다(`PostControllerIT` 등 참조).
- **HtmlSanitizer 재사용 완료**: 001 후속 보완에서 예고한 대로 `Post.body`, `Comment.content` 모두 `HtmlSanitizer.sanitize()`를 거친다.
- **AI 실패 시 재시도 미지원**: spec.md 범위 외로 명시된 대로 FAILED 상태에서 자동/수동 재시도 로직이 없다. 향후 spec에서 다룰 것.
- **Comment 목록 페이지네이션 부재**: NFR-002는 댓글 목록도 페이지네이션 지원을 요구하지만, plan.md 인터페이스 계약 표에는 `GET /api/posts/{postId}/comments`에 `page`/`size` 쿼리 파라미터가 없다. 서버 내부에서 `findTop100ByPostIdOrderByCreatedAtAsc`로 최대 100건 상한만 두었다. 댓글이 많은 게시글에 대한 명시적 페이지네이션이 필요해지면 API 계약부터 spec.md/plan.md에 먼저 반영할 것.
- **인프로세스 비동기의 유실 가능성**: research.md에 명시된 대로, 애플리케이션이 `PostCreatedEvent` 처리 중 재시작되면 해당 이벤트는 유실되고 게시글은 PENDING에 영구히 남는다. 메시지 브로커 도입 전까지는 감수해야 하는 한계다.
- **인증·인가 미구현**: 001과 동일하게 `X-User-Id` 헤더를 신뢰하는 방식만 사용한다 (spec.md 범위 외).
- **좋아요·조회수·캐싱·검색·모더레이션 미도입**: 모두 spec.md에서 범위 외로 명시했으며, 003(캐싱)·향후 spec에서 다룬다.

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
