# Diff: 003-like-view-count-caching

## 커밋 메시지 한 줄 요약

- **KO**: 게시글 좋아요(토글)·조회수(단순 증가)·인기 목록(좋아요순 상위 10)을 Redis 캐시 기반으로 구현하고, SC-001~SC-009 전체를 단위·슬라이스·통합(Testcontainers MySQL+MongoDB+Redis) 테스트로 검증했다.
- **EN**: Implement post like(toggle)/view-count(simple increment)/popular-list(top 10 by likes) on top of a Redis cache layer, and verify SC-001~SC-009 via unit, slice, and Testcontainers (MySQL+MongoDB+Redis) integration tests.

## 변경 요약

001(MySQL)·002(MongoDB)에 이어 Redis를 세 번째 저장소로 도입해, 쓰기 집약적인 좋아요·조회수 카운트를 캐시에서 원자적으로 처리한 뒤 주기 배치로 MongoDB에 동기화하는 구조를 구현했다. 좋아요는 Redis Set의 `SCARD`를 카운트의 단일 소스로 삼아(별도 카운터를 두지 않아) 카운터-집합 간 drift를 원천적으로 피했고, 인기 랭킹은 `ZADD` 절대값 재기록으로 매 갱신마다 자연 치유(self-healing)되도록 설계했다. 도메인 계층은 001·002와 동일한 원칙(P-001)에 따라 Redis 타입에 의존하지 않으며, `PostPopularityPort`/`PostCachePort` 두 포트 인터페이스로 캐시 접근을 격리했다. 게시글 상세 조회는 캐시 우선(cache-aside) + 실시간 좋아요/조회수 조회를 결합하되, Redis 장애 시 MongoDB에 주기 동기화된 스냅샷 값으로 자동 폴백해 장애 격리(NFR-002)를 만족시켰다. spec.md의 수용 기준(SC-001~SC-009) 전체가 최소 1개 이상의 테스트로 매핑되어 있다.

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `.env.example` | +5 | -0 |
| `build.gradle.kts` | +1 | -0 |
| `docker-compose.yml` | +15 | -0 |
| `src/main/resources/application.yml` | +9 | -0 |
| `src/main/kotlin/com/classplatform/common/config/SchedulingConfig.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/Post.kt` | +44 | -1 |
| `src/main/kotlin/com/classplatform/post/domain/PostPopularityPort.kt` | +25 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostCachePort.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostRanking.kt` | +6 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostSnapshot.kt` | +13 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostRepository.kt` | +2 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/RedisPostPopularityAdapter.kt` | +66 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/RedisPostCacheAdapter.kt` | +29 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PopularityCacheSyncScheduler.kt` | +32 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostMongoDocument.kt` | +2 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostRepositoryImpl.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/post/application/LikePostUseCase.kt` | +24 | -0 |
| `src/main/kotlin/com/classplatform/post/application/UnlikePostUseCase.kt` | +24 | -0 |
| `src/main/kotlin/com/classplatform/post/application/LikeResult.kt` | +6 | -0 |
| `src/main/kotlin/com/classplatform/post/application/ListPopularPostsUseCase.kt` | +27 | -0 |
| `src/main/kotlin/com/classplatform/post/application/PopularPost.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/post/application/PostDetail.kt` | +9 | -0 |
| `src/main/kotlin/com/classplatform/post/application/GetPostUseCase.kt` | +27 | -2 |
| `src/main/kotlin/com/classplatform/post/presentation/PostController.kt` | +45 | -2 |
| `src/main/kotlin/com/classplatform/post/presentation/dto/PostDtos.kt` | +17 | -0 |
| `src/test/kotlin/com/classplatform/post/domain/PostTest.kt` | +37 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/RedisPostPopularityAdapterTest.kt` | +120 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/RedisPostCacheAdapterTest.kt` | +67 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/PopularityCacheSyncSchedulerTest.kt` | +85 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/PostRepositoryImplIT.kt` | +25 | -0 |
| `src/test/kotlin/com/classplatform/post/application/LikePostUseCaseTest.kt` | +57 | -0 |
| `src/test/kotlin/com/classplatform/post/application/UnlikePostUseCaseTest.kt` | +57 | -0 |
| `src/test/kotlin/com/classplatform/post/application/ListPopularPostsUseCaseTest.kt` | +62 | -0 |
| `src/test/kotlin/com/classplatform/post/application/GetPostUseCaseTest.kt` | +67 | -13 |
| `src/test/kotlin/com/classplatform/post/presentation/PostControllerTest.kt` | +59 | -1 |
| `src/test/kotlin/com/classplatform/post/presentation/PostControllerIT.kt` | +39 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostLikeIT.kt` | +96 | -0 |
| `src/test/kotlin/com/classplatform/post/PostLikeConcurrencyIT.kt` | +97 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostPopularIT.kt` | +109 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostPopularityFailureIT.kt` | +78 | -0 |

**합계**: 40 files changed, 1512 insertions(+), 19 deletions(-)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(002 완료 직후, 003 구현 착수 직전)과 재생성 명령만 기록한다.

- base commit: `d8727d0` ([test] Phase 4 SC-XXX 검증 테스트 및 002 spec 구현 완료 기록)
- 재생성 명령: `git diff d8727d0 -- src/main/kotlin/com/classplatform/post src/main/kotlin/com/classplatform/common/config/SchedulingConfig.kt src/test/kotlin/com/classplatform/post build.gradle.kts docker-compose.yml .env.example src/main/resources/application.yml`
