# Research: like-view-count-caching

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

### 클래스·모듈 계층 구조

- `Post`(`src/main/kotlin/com/classplatform/post/domain/Post.kt`)는 `private constructor` + 정적 팩토리(`register()`/`reconstitute()`)로 생성이 제한된 concrete 클래스다(추상 클래스 아님, 상속 트리 없음). 현재 필드는 `id, title, body, authorId, aiStatus, tags, summary` 7개이며 `likeCount`/`viewCount`가 없다.
- `PostRepository`(도메인 인터페이스)는 `save/findById/findAll/deleteById` 4개 메서드만 있다. 좋아요·조회수 갱신을 위한 원자적 업데이트 메서드가 없다.
- `PostMongoDocument`(`post/infrastructure/PostMongoDocument.kt`)는 `@CreatedDate`/`@LastModifiedDate`로 `createdAt`/`updatedAt`을 관리하며, 그 외 필드는 `Post`와 1:1 대응한다.
- `PostRepositoryImpl.save()`는 Mongo `save()`가 문서 전체를 덮어쓰는 특성 때문에, id가 있으면 기존 `createdAt`을 조회해 보존하는 로직이 있다(002 T005 이슈). 새 필드(`likeCount`/`viewCount`)를 추가할 때도 동일한 덮어쓰기 위험이 있다 — 아래 "엣지 케이스" 참조.
- `GetPostUseCase.execute()`는 단순히 `postRepository.findById()` 결과를 그대로 반환한다. 캐시 조회·조회수 증가 로직을 끼워 넣을 지점이다.

### Post.reconstitute() 호출부 전수 열거 (Breaking Change 영향 범위)

`Post.reconstitute()` 시그니처에 `likeCount`/`viewCount`를 추가하면(Breaking change) 아래 8개 파일의 호출부를 모두 수정해야 한다.

| 파일 | 비고 |
|---|---|
| `post/infrastructure/PostRepositoryImpl.kt` | 프로덕션 코드. Mongo 문서 → 도메인 변환 |
| `post/application/UpdatePostUseCaseTest.kt` | 테스트 fixture |
| `post/application/DeletePostUseCaseTest.kt` | 테스트 fixture |
| `post/application/EnrichPostUseCaseTest.kt` | 테스트 fixture |
| `post/application/GetPostUseCaseTest.kt` | 테스트 fixture |
| `post/application/ListPostsUseCaseTest.kt` | 테스트 fixture |
| `post/application/CreatePostUseCaseTest.kt` | 테스트 fixture (내부 헬퍼가 `reconstitute` 사용) |
| `post/presentation/PostControllerTest.kt` | 테스트 fixture (`samplePost()`) |

기본값(`likeCount: Long = 0, viewCount: Long = 0`)을 파라미터에 부여하면 기존 8개 호출부 중 새 필드를 명시적으로 검증할 필요가 없는 곳은 수정 없이 컴파일된다 — 방어적 설계로 채택할 만하다(plan.md에서 결정).

### 영향 범위 분석 (신규/수정 파일)

| 파일 | 변경 유형 | 비고 |
|---|---|---|
| `post/domain/Post.kt` | 수정 | `likeCount`/`viewCount` 필드 추가, `applyPopularitySnapshot()`류 메서드로 캐시 동기화 결과 반영 |
| `post/domain/PostPopularityPort.kt` | 신규 | 캐시(Redis) 접근 포트 인터페이스. SDK/Redis 타입 비의존(P-001) |
| `post/infrastructure/PostMongoDocument.kt` | 수정 | `likeCount`/`viewCount` 필드 추가 |
| `post/infrastructure/PostRepositoryImpl.kt` | 수정 | 매핑 갱신. 부분 업데이트(좋아요 카운트 동기화) 시 `createdAt` 보존 트릭과 동일한 주의 필요 |
| `post/infrastructure/RedisPostPopularityAdapter.kt` | 신규 | `PostPopularityPort` 구현체. `StringRedisTemplate`/`RedisTemplate` 사용 |
| `post/infrastructure/PopularityCacheSyncScheduler.kt` | 신규 | `@Scheduled`로 Redis 카운트를 주기적으로 `PostRepository`에 반영 |
| `post/application/LikePostUseCase.kt`, `UnlikePostUseCase.kt` | 신규 | 토글이므로 두 유스케이스로 분리(또는 단일 Toggle유스케이스 — plan.md에서 결정) |
| `post/application/GetPostUseCase.kt` | 수정 | 조회 시 `PostPopularityPort`로 조회수 증가 + 실시간 좋아요/조회수 병합. Redis 장애 시 Post의 저장된 스냅샷 값으로 폴백(NFR-002) |
| `post/application/ListPopularPostsUseCase.kt` | 신규 | Redis Sorted Set에서 상위 10건 조회 |
| `post/presentation/PostController.kt`, `post/presentation/dto/PostDtos.kt` | 수정 | 좋아요/취소/인기목록 엔드포인트, 응답에 `likeCount`/`viewCount` 추가 |
| `build.gradle.kts` | 수정 | `spring-boot-starter-data-redis` 추가 |
| `docker-compose.yml`, `.env.example` | 수정 | Redis 컨테이너 추가 |
| `src/main/resources/application.yml` | 수정 | `spring.data.redis.*`, 동기화 스케줄 주기 설정값 |

## 기술 선택 조사

### Redis 클라이언트: Spring Data Redis (Lettuce)

- 프로젝트가 Spring MVC(비-리액티브) 스택이므로 `spring-boot-starter-data-redis`가 기본 제공하는 Lettuce 기반 `RedisTemplate`/`StringRedisTemplate`을 사용한다. 002가 `anthropic-java`(공식 SDK)를 직접 다루듯, 003은 Spring Boot의 표준 스타터를 그대로 사용하는 편이 일관적이다(별도 Redis 클라이언트 라이브러리를 직접 다룰 이유가 없음).
- Jedis 대안도 있으나 Spring Boot 2.0+ 기본값이 Lettuce이고 별道 선정 사유가 없어 채택하지 않는다.

### 좋아요(토글) 자료구조: Redis Set

- `SADD post:like:{postId} {userId}` — 반환값 1이면 신규 추가(좋아요 성공), 0이면 이미 존재(중복 무시 → FR-003).
- `SREM post:like:{postId} {userId}` — 좋아요 취소.
- `SCARD post:like:{postId}` — 현재 좋아요 수. **별도 INCR 카운터를 두지 않고 `SCARD`를 카운트의 단일 소스로 삼는다** — 카운터와 멤버십 집합을 이중으로 관리하면 두 값이 어긋날(drift) 위험이 있기 때문이다. `SISMEMBER`로 특정 사용자의 좋아요 여부도 바로 조회 가능해 "이미 좋아요를 눌렀는지" 확인에도 재사용된다.
- 조회수는 중복 방지가 없으므로(FR-005) 사용자 단위 추적이 불필요해 단순 `INCR post:view:{postId}` 카운터로 충분하다.

### 인기 게시글 랭킹: Redis Sorted Set

- `ZADD post:popular {likeCount} {postId}` 또는 `SCARD` 결과로 매 좋아요/취소 시 `ZADD`(덮어쓰기)한다. `ZINCRBY`로 증분하는 방식도 가능하나, `SCARD`가 이미 정확한 절대값을 주므로 좋아요/취소 처리 직후 `ZADD post:popular {SCARD 결과} {postId}`로 절대값을 다시 쓰는 편이 두 자료구조 간 drift 위험을 줄인다(SADD/SREM과 ZADD 두 명령이 원자적이진 않지만, 매번 절대값으로 덮어쓰므로 최종 상태는 항상 SCARD와 일치하게 수렴한다).
- `ZREVRANGE post:popular 0 9 WITHSCORES`로 좋아요 수 내림차순 상위 10건(FR-007, SC-006)을 조회한다.

### Redis→MongoDB 동기화: `@Scheduled` 주기 배치

- 001/002는 `@EnableAsync`(이벤트 기반)만 사용했고 `@EnableScheduling`은 아직 미사용이다. `@Scheduled(fixedDelay = ...)`로 전체 게시글의 Redis 카운트를 순회하며 `PostMongoDocument`에 반영하는 배치 잡을 신설한다.
- 순회 대상(어떤 postId들을 동기화할지) 파악 방법: Redis 자체에 "변경된 postId 집합"을 별도로 추적(예: `SADD post:dirty {postId}` on 카운트 변경, 배치가 이 집합을 `SPOP`하며 처리)하는 방식이 전체 게시글 스캔보다 효율적이다. 세부 설계는 plan.md에서 확정한다.

## 엣지 케이스 및 한계

- **Redis 데이터 휘발성**: Redis가 영속화 설정(AOF/RDB) 없이 재시작되면 아직 MongoDB에 동기화되지 않은 좋아요·조회수 증분이 유실된다. 002의 "인프로세스 이벤트 유실 가능성"과 같은 계열의 한계로, 이번 spec에서도 동일하게 감수하고 문서화한다(메시지 큐·영속 큐 도입은 범위 외).
- **SADD/ZADD 비원자성**: 좋아요 처리 시 `SADD`와 `ZADD`(랭킹 갱신)가 별개 명령이라 그 사이에 장애가 발생하면 랭킹만 갱신 실패할 수 있다. 다음 좋아요/취소 요청이나 주기 동기화 배치가 다시 절대값으로 덮어쓰므로 자연 치유(self-healing)되지만, 그 사이 랭킹이 짧게 부정확할 수 있다.
- **동시 좋아요/취소 레이스**: `SADD`/`SREM`은 Redis 단일 스레드 모델 덕분에 개별 명령 자체는 원자적이다(NFR-001 충족). 다만 "좋아요 여부 조회 후 토글 결정" 같은 조합 로직을 애플리케이션에서 짜면 TOCTOU가 생길 수 있으므로, `SADD`/`SREM`의 반환값(신규 추가 여부)만으로 토글 결과를 판단하고 별도의 사전 조회-후-처리 패턴을 쓰지 않는다.
- **캐시 미스 시 최초 응답 지연**: NFR-005(캐시 우선 조회)를 만족하려면 캐시 미스 시 MongoDB 조회 후 캐시 적재(cache-aside)가 필요하다. 이 최초 1회는 캐시 히트보다 느리다 — 정상적인 트레이드오프로 별도 완화책은 범위 외.
