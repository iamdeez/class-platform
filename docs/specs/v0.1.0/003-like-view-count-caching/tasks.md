# Tasks: like-view-count-caching

> Branch: 003-like-view-count-caching | Date: 2026-07-12 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 002 완료 후 "MongoDB 도입 완료", "AI 실패 재시도 미지원", "댓글 목록 페이지네이션 부재" 등을 확인했다. 003은 여기에 Redis를 추가로 도입한다.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. 의존 관계가 있는 태스크는 반드시 선행 태스크 완료 후 실행한다.

### Phase 1. 기반 작업

- [x] **T001** — Redis 로컬 기동 구성 추가
  - 구현 파일: `docker-compose.yml`(수정), `.env.example`(수정), `src/main/resources/application.yml`(수정)
  - 관련 요구사항: 없음
  - 상세: `redis:7-alpine` 컨테이너 추가(포트 6379, healthcheck `redis-cli ping`). `spring.data.redis.host`/`port` 설정 및 동기화 배치 주기(`popularity-cache.sync-interval-ms`, 기본값 10000)를 설정값으로 분리
  - 완료 기준: `docker compose up -d` 후 애플리케이션이 Redis 연결에 성공한다 — 확인 완료. T002(Gradle 의존성)가 있어야 실제 연결 시도가 발생하므로(Redis 코드가 아직 없는 상태에서는 `LettuceConnectionFactory`가 지연 초기화되어 커넥션 자체가 열리지 않음), 임시 스모크 테스트(`StringRedisTemplate`로 SET/GET)를 작성해 실행 후 삭제하는 방식으로 검증했다

- [x] **T002** `[P]` — Gradle 의존성 추가
  - 구현 파일: `build.gradle.kts`
  - 상세: `spring-boot-starter-data-redis` 추가. Redis Testcontainers는 공식 모듈이 없으며 `org.testcontainers:mysql`/`mongodb`가 이미 core(`GenericContainer` 포함)를 전이 의존성으로 가져오므로 별도 추가 불필요 — 확인만 한다
  - 완료 기준: `./gradlew build` 성공 — 확인 완료. `./gradlew dependencies --configuration testRuntimeClasspath`로 `org.testcontainers:testcontainers` core가 이미 전이 의존성에 포함됨을 확인했다

### Phase 2. 핵심 구현 — 캐시 포트 및 도메인

- [x] **T003** — PostPopularityPort + RedisPostPopularityAdapter 구현 (T002 완료 후)
  - 구현 파일: `post/domain/PostPopularityPort.kt`(신규), `post/infrastructure/RedisPostPopularityAdapter.kt`(신규)
  - 관련 요구사항: `FR-001`~`FR-007`, `NFR-001`
  - 상세: 포트 메서드 — `addLike(postId, userId): Boolean`(신규 추가 여부), `removeLike(postId, userId): Boolean`, `isLiked(postId, userId): Boolean`, `getLikeCount(postId): Long`(SCARD), `incrementViewCount(postId): Long`(INCR), `getViewCount(postId): Long`, `refreshRanking(postId, likeCount)`(ZADD 절대값), `getTopPostIds(limit): List<String>`(ZREVRANGE), `markDirty(postId)`/`consumeDirty(): Set<String>`. 어댑터는 `StringRedisTemplate` 사용
  - 완료 기준: 단위 테스트에서 `StringRedisTemplate`을 MockK로 대체해 각 메서드가 올바른 Redis 커맨드(opsForSet/opsForValue/opsForZSet)를 호출하는지 검증 (실제 Redis 연결 없음) — `RedisPostPopularityAdapterTest` 11건 통과
  - **구현 노트**: `userId`는 도메인 전체에서 일관되게 쓰는 `UserId` 값 객체를 그대로 포트 시그니처에 사용했다(plan.md에는 명시하지 않았으나 `Post.authorId`와 동일한 타입 일관성 유지). `getViewCount()`는 저장된 값이 없으면(신규 게시글) `null`을 0으로 처리해 방어한다.

- [x] **T004** — Post 도메인 모델 확장 (T003과 무관, 병렬 가능) `[P]`
  - 구현 파일: `post/domain/Post.kt`(수정)
  - 관련 요구사항: `FR-004`, `FR-006`
  - 상세: `likeCount: Long`, `viewCount: Long` 필드 추가(기본값 0). `register()`는 항상 0으로 시작. `reconstitute()`에 `likeCount: Long = 0, viewCount: Long = 0` 파라미터 추가(기본값으로 기존 8개 호출부 하위 호환). `applyPopularitySnapshot(likeCount: Long, viewCount: Long)` 메서드로 두 값을 함께 갱신(음수 방지 `require`)
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다. `applyPopularitySnapshot()` 음수 검증 단위 테스트 통과 — `PostTest`에 4건 추가(0 시작, 스냅샷 반영, 좋아요/조회수 음수 거부 각각), 기존 8개 `reconstitute()` 호출부 모두 수정 없이 컴파일됨을 확인

- [x] **T005** — PostRepository 확장 + MongoDB 매핑 갱신 (T004 완료 후)
  - 구현 파일: `post/domain/PostRepository.kt`(수정), `post/infrastructure/PostMongoDocument.kt`(수정), `post/infrastructure/PostRepositoryImpl.kt`(수정)
  - 관련 요구사항: `FR-007`
  - 상세: `PostRepository`에 `findAllByIds(ids: List<String>): List<Post>` 추가(인기 목록 배치 조회, P-002 N+1 방지). `PostMongoDocument`에 `likeCount`/`viewCount` 필드 추가(기본 0). `PostRepositoryImpl.save()`의 기존 `createdAt` 보존 트릭과 동일하게, 부분 필드 갱신 시 다른 필드가 유실되지 않는지 재확인
  - 완료 기준: Testcontainers 통합 테스트로 `findAllByIds()` 배치 조회 및 `likeCount`/`viewCount` 저장·조회 왕복 확인 — `PostRepositoryImplIT`에 2건 추가, 전체 4건 통과
  - **구현 노트**: `PostMongoRepository.kt`는 수정하지 않았다 — `MongoRepository`가 상속하는 `CrudRepository`에 이미 `findAllById(ids): Iterable<T>`가 내장되어 있어, `PostRepositoryImpl.findAllByIds()`가 이를 그대로 위임하기만 하면 된다(plan.md/tasks.md 작성 시점에는 파악하지 못했던 사실).

### Phase 3. 핵심 구현 — 유스케이스 및 API

- [ ] **T006** — LikePostUseCase, UnlikePostUseCase 구현 (T003, T005 완료 후)
  - 구현 파일: `post/application/LikePostUseCase.kt`, `UnlikePostUseCase.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 상세: 둘 다 `PostRepository.findById()`로 게시글 존재를 먼저 확인(404). `PostPopularityPort.addLike()`/`removeLike()` 호출 후 `getLikeCount()` + `refreshRanking()`으로 랭킹 갱신, `markDirty()`로 동기화 대상 등록
  - 완료 기준: 각 유스케이스 단위 테스트(MockK로 `PostPopularityPort` 대체) 통과. 존재하지 않는 게시글에 대한 404(`PostNotFoundException`) 케이스 포함

- [ ] **T007** — GetPostUseCase 수정: 캐시 조회 + 조회수 증가 + 장애 폴백 (T003, T005 완료 후)
  - 구현 파일: `post/application/GetPostUseCase.kt`(수정), `post/application/PostDetail.kt`(신규, 값 객체)
  - 관련 요구사항: `FR-004`, `FR-005`, `FR-006`, `NFR-002`, `NFR-005`
  - 상세: 반환 타입을 `Post`에서 `PostDetail(post, likeCount, viewCount)`로 변경. `incrementViewCount()`는 실패해도 조회 자체를 막지 않도록 예외를 흡수. `getLikeCount()`/`getViewCount()` 호출이 예외를 던지면 `post.likeCount`/`post.viewCount`(저장된 스냅샷)로 폴백
  - 완료 기준: 단위 테스트(MockK)로 (1) 정상 경로에서 Redis 실시간 값 사용, (2) `PostPopularityPort` 예외 발생 시 스냅샷 값으로 폴백(SC-007 단위 수준), (3) 조회수 증가 호출 검증

- [ ] **T008** — ListPopularPostsUseCase 구현 (T003, T005 완료 후, T007과 병렬 가능) `[P]`
  - 구현 파일: `post/application/ListPopularPostsUseCase.kt`, `post/application/PopularPost.kt`(값 객체)
  - 관련 요구사항: `FR-007`, `NFR-003`
  - 상세: `PostPopularityPort.getTopPostIds(10)` → `PostRepository.findAllByIds()`로 배치 조회 → Redis가 반환한 순서(좋아요 수 내림차순)대로 재정렬
  - 완료 기준: 단위 테스트(MockK)로 정렬 순서 보존과 N+1 미발생(배치 조회 1회 호출) 확인

- [ ] **T009** — PopularityCacheSyncScheduler 구현 (T003, T005 완료 후)
  - 구현 파일: `post/infrastructure/PopularityCacheSyncScheduler.kt`, `common/AiEnrichmentConfig.kt` 또는 별도 `SchedulingConfig.kt`(`@EnableScheduling` 추가)
  - 관련 요구사항: `NFR-004`
  - 상세: `@Scheduled(fixedDelayString = "\${popularity-cache.sync-interval-ms}")`로 `consumeDirty()`가 반환한 postId들의 `likeCount`/`viewCount`를 조회해 `Post.applyPopularitySnapshot()` 후 저장
  - 완료 기준: 단위 테스트(MockK)로 dirty set에 담긴 postId들이 모두 처리되고 저장소에 반영되는지 확인 (실제 스케줄 트리거 없이 메서드 직접 호출로 검증)

- [ ] **T010** — PostController 및 DTO 확장 (T006, T007, T008 완료 후)
  - 구현 파일: `post/presentation/PostController.kt`(수정), `post/presentation/dto/PostDtos.kt`(수정)
  - 관련 요구사항: `FR-001`~`FR-007`
  - 상세: `POST/DELETE /api/posts/{postId}/likes`, `GET /api/posts/popular` 추가. `PostResponse`에 `likeCount`/`viewCount` 필드 추가(기존 필드는 유지 — P-003)
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

### Phase 4. 테스트 (SC-XXX 검증)

- [ ] **T011** — SC-001, SC-002 통합 테스트 (T010 완료 후)
  - 테스트 파일: `post/presentation/PostLikeIT.kt`
  - 검증 대상: `SC-001`, `SC-002`
  - 시나리오: 동일 사용자 중복 좋아요 요청 시 카운트 1만 증가, 좋아요 후 취소 시 원상복구 (Testcontainers Redis: `GenericContainer("redis:7-alpine")`)

- [ ] **T012** — SC-008 동시성 통합 테스트 (T010 완료 후, T011과 병렬 가능) `[P]`
  - 테스트 파일: `post/PostLikeConcurrencyIT.kt`
  - 검증 대상: `SC-008`
  - 시나리오: 서로 다른 사용자 100명이 동일 게시글에 동시 좋아요 요청 → 최종 좋아요 수 100 확인 (001의 `EnrollmentConcurrencyIT` 패턴 재사용)

- [ ] **T013** — SC-003, SC-004, SC-005 통합 테스트 (T010 완료 후, T011·T012와 병렬 가능) `[P]`
  - 테스트 파일: `post/presentation/PostControllerIT.kt` (기존 파일에 케이스 추가)
  - 검증 대상: `SC-003`, `SC-004`, `SC-005`
  - 시나리오: 게시글 상세 응답에 `likeCount`/`viewCount` 포함 확인, 3회 조회 시 `viewCount` 3 증가 확인

- [ ] **T014** — SC-006 통합 테스트 (T010 완료 후, 위 태스크들과 병렬 가능) `[P]`
  - 테스트 파일: `post/presentation/PostPopularIT.kt`
  - 검증 대상: `SC-006`
  - 시나리오: 좋아요 수가 다른 게시글 3건 이상 시드 후 인기 목록이 좋아요 수 내림차순, 최대 10건으로 반환되는지 확인

- [ ] **T015** — SC-007 통합 테스트 (T010 완료 후, 위 태스크들과 병렬 가능) `[P]`
  - 테스트 파일: `post/presentation/PostPopularityFailureIT.kt`
  - 검증 대상: `SC-007`
  - 시나리오: `PostPopularityPort`를 `@MockkBean`으로 교체해 모든 메서드가 예외를 던지도록 스텁 → 게시글 상세 조회가 200으로 정상 응답하고 저장된 스냅샷 값을 반환하는지 확인

- [ ] **T016** `[P]` — SC-009 단위 테스트
  - 테스트 파일: `post/application/GetPostUseCaseTest.kt` (기존 파일에 케이스 추가, T007에서 일부 선행 작성)
  - 검증 대상: `SC-009`
  - 시나리오: 캐시 히트 상황을 MockK로 구성해 동일 postId 재조회 시 `PostRepository.findById()`가 1회만 호출되는지 확인

## 구현 완료 기준

- [ ] 모든 태스크 체크박스가 완료 처리되었다.
- [ ] `./gradlew test`가 전체 PASSED를 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다.
