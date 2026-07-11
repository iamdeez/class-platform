# Plan: like-view-count-caching

> Branch: 003-like-view-count-caching | Date: 2026-07-12 | Spec: [spec.md](./spec.md)

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

- [x] **P-001 도메인 순수성**: `post/domain`은 Redis 클라이언트 타입에 의존하지 않는다. `PostPopularityPort`는 도메인 인터페이스이고, 구현체(`RedisPostPopularityAdapter`)는 infrastructure에 위치한다.
- [x] **P-002 성능 원칙**: 인기 게시글 목록 조회 시 Redis에서 얻은 postId 목록으로 MongoDB를 N+1 조회하지 않도록 `PostRepository.findAllByIds()` 배치 조회 메서드를 신설한다. Redis→MongoDB 동기화 배치도 전체 게시글 스캔 대신 "변경된 postId 집합"(dirty set)만 순회해 불필요한 조회를 피한다.
- [x] **P-003 호환성 원칙**: `PostResponse`에 `likeCount`/`viewCount` 필드를 추가하는 것은 additive change로 기존 API 계약을 깨지 않는다. `Post.reconstitute()` 시그니처 변경(도메인 내부 API)은 외부 REST 계약과 무관하므로 P-003 대상이 아니다.
- [x] **P-004 테스트 원칙**: spec.md의 모든 FR(001~007)이 SC-001~SC-009에 매핑되어 있다.
- [x] **P-005 스펙 범위 원칙**: 댓글/강의 좋아요, 조회수 어뷰징 방지, 캐시 워밍은 spec.md "범위 외"에 명시했고 본 plan에 포함하지 않는다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **언어 / 런타임**: Kotlin 1.9.25, Spring Boot 3.3.4, JDK 17 (기존과 동일)
- **주요 의존성 (신규)**: `spring-boot-starter-data-redis`(Lettuce 클라이언트, Spring MVC 비-리액티브 스택 기준 기본값)
- **캐시**: Redis 7 (로컬 Docker 컨테이너), `StringRedisTemplate`/`RedisTemplate`으로 접근
- **테스트 프레임워크**: JUnit5 + MockK + Testcontainers. Redis는 공식 Testcontainers 모듈이 없어 `GenericContainer("redis:7-alpine")`(포트 6379)로 기동한다.

## 사전 영향도 분석 결과

research.md의 "영향 범위 분석" 표를 기준으로 한다. 핵심 요약:

| 파일 | 변경 유형 |
|---|---|
| `post/domain/Post.kt` | 수정 (`likeCount`/`viewCount` 필드 추가, `reconstitute()` 시그니처 변경 — 기본값 0으로 하위 호출부 영향 최소화) |
| `post/domain/PostRepository.kt` | 수정 (`findAllByIds()` 추가) |
| `post/domain/PostPopularityPort.kt` | 신규 |
| `post/infrastructure/{PostMongoDocument, PostRepositoryImpl}.kt` | 수정 |
| `post/infrastructure/RedisPostPopularityAdapter.kt` | 신규 |
| `post/infrastructure/PopularityCacheSyncScheduler.kt` | 신규 |
| `post/application/{LikePost,UnlikePost,ListPopularPosts}UseCase.kt` | 신규 |
| `post/application/GetPostUseCase.kt` | 수정 (반환 타입을 `PostDetail`로 변경, 조회수 증가 트리거) |
| `post/presentation/PostController.kt`, `post/presentation/dto/PostDtos.kt` | 수정 |
| `build.gradle.kts`, `docker-compose.yml`, `.env.example`, `application.yml` | 수정 |

research.md의 "Post.reconstitute() 호출부 전수 열거" 표(8개 파일)를 구현 단계에서 그대로 사용한다 — 기본값 파라미터 채택으로 실제 수정이 필요한 곳은 신규 필드를 명시적으로 검증하는 테스트뿐이다.

## 핵심 설계

### Redis 키 설계

| 키 | 타입 | 용도 |
|---|---|---|
| `post:like:{postId}` | Set\<String\>(userId) | 좋아요한 사용자 집합. `SCARD`가 좋아요 수의 단일 소스(카운터 이중 관리로 인한 drift 방지) |
| `post:view:{postId}` | String (카운터) | 조회수. `INCR`로 증가 |
| `post:popular` | Sorted Set | score=좋아요 수, member=postId. 좋아요/취소 직후 `SCARD` 결과로 절대값 재기록(`ZADD`) |
| `post:dirty` | Set\<String\>(postId) | 동기화 대상 추적. 카운트 변경 시 추가, 배치가 소비 |
| `post:cache:{postId}` | String (JSON) | 게시글 상세 캐시(cache-aside, TTL 적용) |

### 좋아요 토글 흐름 (FR-001~003)

```
LikePostUseCase.execute(postId, userId)
      ↓ PostRepository.findById(postId) — 존재 확인 (404)
      ↓ PostPopularityPort.addLike(postId, userId) → Redis SADD (신규 추가 시 1, 이미 존재 시 0 반환)
      ↓ PostPopularityPort.getLikeCount(postId) → Redis SCARD
      ↓ PostPopularityPort.refreshRanking(postId, likeCount) → Redis ZADD(절대값)
      ↓ 응답: { liked: true, likeCount }
```

`UnlikePostUseCase`는 `SADD` 대신 `SREM`을 쓰는 대칭 흐름이다. 두 유스케이스 모두 "좋아요 여부를 먼저 조회한 뒤 분기"하지 않고 `SADD`/`SREM`의 반환값만으로 상태를 판단해 TOCTOU를 피한다(research.md 참조).

### 게시글 상세 조회 흐름 (FR-004~006, NFR-002, NFR-005)

```
GetPostUseCase.execute(postId): PostDetail
      ↓ PostPopularityPort.incrementViewCount(postId) → Redis INCR (실패해도 무시하고 계속 진행 — 조회 자체를 막지 않음)
      ↓ 캐시(post:cache:{postId}) 조회 → 히트 시 그 값 사용, 미스 시 PostRepository.findById() 후 캐시 적재
      ↓ PostPopularityPort.getLikeCount/getViewCount 조회 시도
            성공 → 실시간 값 사용
            실패(Redis 장애) → Post에 저장된 likeCount/viewCount 스냅샷 값으로 폴백 (NFR-002)
      ↓ PostDetail(post, likeCount, viewCount) 반환
```

`PostDetail`은 `post/application` 패키지에 두는 값 객체다(도메인 개념이 아닌 "실시간 값 vs 스냅샷 값"의 합성은 애플리케이션 레이어의 책임).

### 인기 게시글 목록 (FR-007, NFR-003)

```
ListPopularPostsUseCase.execute(): List<PopularPost>
      ↓ PostPopularityPort.getTopPostIds(10) → Redis ZREVRANGE
      ↓ PostRepository.findAllByIds(postIds) → MongoDB 배치 조회 (N+1 방지, P-002)
      ↓ Redis 반환 순서(좋아요 수 내림차순)를 기준으로 재정렬 후 매핑
```

### Redis → MongoDB 동기화 배치

```
PopularityCacheSyncScheduler (@Scheduled)
      ↓ Redis post:dirty에서 postId 소비(SPOP 또는 SMEMBERS + SREM)
      ↓ 각 postId에 대해 PostPopularityPort.getLikeCount/getViewCount 조회
      ↓ PostRepository.findById → Post.applyPopularitySnapshot(likeCount, viewCount) → save()
```

동기화 주기는 `application.yml`의 설정값으로 분리한다(하드코딩 금지, 001/002의 관례).

## 인터페이스 계약

사용자 식별은 001/002와 동일하게 `X-User-Id` 헤더를 신뢰한다.

| Method | Path | 요청 | 응답 | 관련 FR/SC |
|---|---|---|---|---|
| POST | `/api/posts/{postId}/likes` | - | `200 {liked: true, likeCount}` / `404` | FR-001, FR-003, SC-001 |
| DELETE | `/api/posts/{postId}/likes` | - | `200 {liked: false, likeCount}` / `404` | FR-002, SC-002 |
| GET | `/api/posts/{postId}` (기존 확장) | - | `200 {post, aiStatus, tags, summary, likeCount, viewCount}` / `404` | FR-004~006, SC-003~005, SC-007, SC-009 |
| GET | `/api/posts/popular` | - | `200 {items: [{postId, title, likeCount}]}` (최대 10건) | FR-007, SC-006 |

`GET /api/posts/popular`는 `GET /api/posts/{postId}`보다 먼저 매칭되도록 Spring의 리터럴 경로 우선 라우팅에 의존한다(Spring MVC는 `{postId}` 같은 변수 패턴보다 리터럴 패턴을 항상 더 구체적으로 취급하므로 별도 순서 조정 없이 안전하게 공존한다).

## 데이터 모델

### `posts` 컬렉션 (필드 추가)

| 필드 | 타입 | 비고 |
|---|---|---|
| `likeCount` | Long | 기본 0. Redis→MongoDB 주기 동기화로 갱신되는 스냅샷(실시간 값은 Redis가 소스) |
| `viewCount` | Long | 기본 0. 위와 동일 |

### Redis 데이터 구조

"핵심 설계 > Redis 키 설계" 표 참조. 별도 스키마 마이그레이션 불필요(스키마리스 캐시).

## 테스트 전략

| SC 식별자 | 테스트 유형 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 통합 테스트 (Testcontainers Redis) | 동일 사용자가 좋아요를 2회 요청 | 같은 postId·userId로 POST 2회 | likeCount는 1만 증가 |
| SC-002 | 통합 테스트 | 좋아요 후 취소 | POST → DELETE | likeCount가 원래 값으로 복귀 |
| SC-003 | 통합 테스트 (API) | 게시글 상세 응답 확인 | 좋아요 1건 존재 | 응답에 `likeCount` 포함 |
| SC-004 | 통합 테스트 (API) | 동일 게시글 3회 조회 | GET 3회 | `viewCount` 3 증가 |
| SC-005 | 통합 테스트 (API) | 게시글 상세 응답 확인 | 정상 조회 | 응답에 `viewCount` 포함 |
| SC-006 | 통합 테스트 (API) | 인기 목록 조회 | 좋아요 수가 다른 게시글 3건 이상 시드 | 좋아요 수 내림차순, 최대 10건 |
| SC-007 | 통합 테스트 | Redis 연결 불가 상태에서 상세 조회 | `PostPopularityPort`를 예외 발생 Mock으로 대체 | 200 응답, 저장된 스냅샷 값으로 응답 |
| SC-008 | 통합 테스트 (동시성, Testcontainers Redis) | 서로 다른 사용자 100명이 동시에 좋아요 | 100 스레드 동시 POST | 최종 `SCARD` 결과와 응답의 `likeCount`가 100으로 일치 |
| SC-009 | 단위 테스트 (application) | 캐시 히트 시 저장소 접근 여부 | `PostRepository`를 MockK로 대체, 동일 postId 2회 조회 | `findById` 호출 1회만 발생(2번째는 캐시 히트) |

## 기타 고려사항

- **Redis 장애 격리 검증 방법**: SC-007은 실제 Testcontainers Redis 컨테이너를 중지시키거나, `PostPopularityPort`를 `@MockkBean`으로 교체해 모든 메서드가 예외를 던지도록 스텁하는 두 가지 방법이 있다. 후자가 더 결정론적이라 우선 채택하고, 필요 시 전자로 보강한다.
- **캐시 TTL 정책**: `post:cache:{postId}`의 TTL 구체값(예: 5분/10분)은 이 문서에서 확정하지 않는다 — tasks.md 구현 시 `application.yml` 설정값으로 분리하고 합리적 기본값(예: 5분)을 사용한다.
- **`likeCount`/`viewCount` 음수 방지**: `Post.applyPopularitySnapshot()`은 Course/Enrollment의 기존 관례처럼 `require(likeCount >= 0)` 등으로 도메인 불변식을 검증한다.
