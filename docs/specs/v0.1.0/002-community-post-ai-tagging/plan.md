# Plan: community-post-ai-tagging

> Branch: 002-community-post-ai-tagging | Date: 2026-07-09 | Spec: [spec.md](./spec.md)

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

`class-platform/.claude/docs/constitution.md`의 5개 조항 기준.

- [x] **P-001 도메인 순수성**: `post/domain`, `comment/domain`은 Spring/MongoDB 드라이버/Claude SDK에 의존하지 않는다. `AiTaggingPort`는 도메인 인터페이스이고 구현체(`ClaudeAiTaggingClient`)는 infrastructure에 위치한다.
- [x] **P-002 성능(조정)**: 게시글 등록은 AI 처리를 기다리지 않고 즉시 응답(NFR-001)하므로 외부 API 호출이 핵심 경로 지연을 유발하지 않는다.
- [x] **P-003 호환성(조정)**: 신규 API이며 001 spec의 계약과 충돌하지 않는다. `common.UserId`를 재사용한다.
- [x] **P-004 테스트**: FR-001~011 전체가 SC-001~SC-011에 매핑되어 검증 시나리오를 갖췄다.
- [x] **P-005 스펙 범위**: 좋아요/캐싱/검색/모더레이션/재시도는 spec.md "범위 외"에 명시했고 본 plan에 포함하지 않는다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **언어 / 런타임**: Kotlin 1.9, JDK 17 (001과 동일)
- **프레임워크**: Spring Boot 3.x (spring-boot-starter-web, spring-boot-starter-data-mongodb, spring-boot-starter-validation)
- **비동기 처리**: Spring `@Async` + `ApplicationEventPublisher` (`@EnableAsync`, `@TransactionalEventListener` 대신 MongoDB는 기본 트랜잭션이 제한적이므로 저장 완료 후 명시적으로 이벤트 발행)
- **AI 연동**: `com.anthropic:anthropic-java` 공식 SDK, 모델 `claude-haiku-4-5`, `output_config.format`(Structured Outputs)으로 `PostEnrichmentResult(tags: List<String>, summary: String)` 응답 수신
- **DB**: MongoDB (posts, comments 컬렉션) — 001과 동일 컨테이너 재사용, 신규 컬렉션만 추가
- **테스트 프레임워크**: JUnit5 + MockK + Testcontainers(MongoDB) + MockMvc. Claude API 호출은 단위/통합 테스트에서 `AiTaggingPort`를 MockK로 대체하여 실제 API 호출 없이 검증한다 (E2E 성격의 실제 API 연동 테스트는 범위 외).

## 사전 영향도 분석 결과

001 spec의 `common` 패키지(`UserId`)를 재사용하는 것 외에는 전부 신규 파일이다.

### 영향 파일 목록

| 파일 | 변경 유형 | 영향 내용 |
|---|---|---|
| `build.gradle.kts` | 수정 | `spring-boot-starter-data-mongodb`, `com.anthropic:anthropic-java` 의존성 추가 |
| `src/main/resources/application.yml` | 수정 | MongoDB 연결 설정, `ANTHROPIC_API_KEY` 환경변수 참조 추가 |
| `docker-compose.yml` | 수정 | MongoDB 컨테이너 추가 (001의 MySQL 컨테이너와 병행) |
| `post/domain/Post.kt`, `PostAiStatus.kt`, `AiTaggingPort.kt`, `exception/*.kt` | 신규 | 게시글 애그리거트, AI 처리 상태, AI 태깅 포트 인터페이스 |
| `post/application/*.kt` | 신규 | CreatePostUseCase, GetPostUseCase, ListPostsUseCase, UpdatePostUseCase, DeletePostUseCase, EnrichPostUseCase |
| `post/infrastructure/PostMongoDocument.kt`, `PostMongoRepository.kt`, `PostRepositoryImpl.kt`, `ClaudeAiTaggingClient.kt`, `PostCreatedEventListener.kt` | 신규 | MongoDB 매핑, Claude API 어댑터, 비동기 이벤트 리스너 |
| `post/presentation/PostController.kt`, `dto/*.kt` | 신규 | REST 엔드포인트 |
| `comment/domain/Comment.kt`, `CommentRepository.kt`, `exception/*.kt` | 신규 | 댓글 애그리거트 |
| `comment/application/*.kt` | 신규 | CreateCommentUseCase, ListCommentsUseCase, DeleteCommentUseCase |
| `comment/infrastructure/CommentMongoDocument.kt`, `CommentMongoRepository.kt`, `CommentRepositoryImpl.kt` | 신규 | MongoDB 매핑 |
| `comment/presentation/CommentController.kt`, `dto/*.kt` | 신규 | REST 엔드포인트 |
| `common/AiEnrichmentConfig.kt` | 신규 | `@EnableAsync` 설정, Claude SDK 클라이언트 빈 등록 |

## 핵심 설계

### 도메인 모델

- **Post (애그리거트 루트)**: `id`, `title`, `body`, `authorId`, `aiStatus: PostAiStatus(PENDING/COMPLETED/FAILED)`, `tags: List<String>`(기본 빈 리스트), `summary: String?`, `createdAt`. `markEnrichmentCompleted(tags, summary)`/`markEnrichmentFailed()` 메서드가 상태 전이를 캡슐화한다.
- **Comment (애그리거트 루트)**: `id`, `postId`, `authorId`, `content`, `createdAt`. Post와 별도 컬렉션·별도 애그리거트로 관리한다 (research.md 참조).

### 유스케이스 흐름 (게시글 등록 → AI 태깅)

```
PostController.create(title, body, userId)
      ↓
CreatePostUseCase.execute() → Post 생성(aiStatus=PENDING) → PostRepository.save()
      ↓ (저장 성공 즉시 응답 반환 — NFR-001)
      ↓ ApplicationEventPublisher.publishEvent(PostCreatedEvent(postId, title, body))
      ↓ (비동기, 별도 스레드)
PostCreatedEventListener.onPostCreated() [@Async]
      ↓
EnrichPostUseCase.execute(postId, title, body)
      ↓ AiTaggingPort.generateTagsAndSummary(title, body) 호출 (ClaudeAiTaggingClient → Claude API)
      ↓ 성공 시: Post.markEnrichmentCompleted(tags, summary) → PostRepository.update()
      ↓ 실패 시(RateLimitException, AnthropicIoException 등 캐치): Post.markEnrichmentFailed() → PostRepository.update()
```

### 레이어드 아키텍처 원칙 (NFR-003)

`post/domain`, `comment/domain`은 Spring·MongoDB·Claude SDK 애노테이션·타입에 의존하지 않는다. `AiTaggingPort`는 도메인이 정의하는 인터페이스(`fun generateTagsAndSummary(title: String, body: String): AiEnrichmentResult`)이고, `ClaudeAiTaggingClient`(infrastructure)가 이를 구현하며 내부에서만 Java SDK 타입을 사용한다. 이를 통해 `EnrichPostUseCase`의 단위 테스트는 `AiTaggingPort`를 MockK로 스텁하여 실제 API 호출 없이 수행한다.

## 인터페이스 계약

사용자 식별은 001과 동일하게 `X-User-Id` 헤더를 신뢰한다.

| Method | Path | 요청 | 응답 | 관련 FR/SC |
|---|---|---|---|---|
| POST | `/api/posts` | `{title, body}` | `201 {postId}` / `400` | FR-001, SC-001, SC-002 |
| GET | `/api/posts?page=&size=` | 쿼리 파라미터 | `200 {items[]}` (최신순) | FR-002, NFR-002, SC-003 |
| GET | `/api/posts/{postId}` | - | `200 {post, aiStatus, tags, summary}` / `404` | FR-003, FR-010, SC-007, SC-008, SC-010 |
| PATCH | `/api/posts/{postId}` | `{title?, body?}` | `200` / `403`(비작성자) | FR-004, SC-004 |
| DELETE | `/api/posts/{postId}` | - | `204` / `403`(비작성자) | FR-005, SC-004 |
| POST | `/api/posts/{postId}/comments` | `{content}` | `201 {commentId}` / `404`(게시글 없음) | FR-006, SC-005 |
| GET | `/api/posts/{postId}/comments` | - | `200 {items[]}` | FR-007, SC-011 |
| DELETE | `/api/comments/{commentId}` | - | `204` / `403`(비작성자) | FR-008, SC-006 |

## 데이터 모델

### `posts` 컬렉션 (MongoDB)

| 필드 | 타입 | 비고 |
|---|---|---|
| `_id` | ObjectId | PK |
| `title` | String | 필수 |
| `body` | String | 필수 |
| `authorId` | Long | 필수 |
| `aiStatus` | String | PENDING(기본) / COMPLETED / FAILED |
| `tags` | Array\<String\> | 최대 5개, AI 처리 완료 전 빈 배열 |
| `summary` | String? | 최대 200자, AI 처리 완료 전 null |
| `createdAt`, `updatedAt` | Date | |

### `comments` 컬렉션 (MongoDB)

| 필드 | 타입 | 비고 |
|---|---|---|
| `_id` | ObjectId | PK |
| `postId` | ObjectId | FK (애플리케이션 레벨 참조, MongoDB 자체 FK 제약 없음) |
| `authorId` | Long | 필수 |
| `content` | String | 필수 |
| `createdAt` | Date | |

## 테스트 전략

| SC 식별자 | 테스트 유형 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 단위 테스트 (domain) | 제목/본문 누락으로 Post 생성 시도 | title="" | 검증 예외 발생 |
| SC-002 | 통합 테스트 (API) | 게시글 등록 응답 시간 측정 | 정상 요청 | AI 처리 완료를 기다리지 않고 즉시 201 응답 (AiTaggingPort를 지연 응답 Mock으로 대체해 검증) |
| SC-003 | 통합 테스트 (API) | 목록 조회 정렬 확인 | 3건 시드 | 최신순 정렬 확인 |
| SC-004 | 통합 테스트 (API) | 비작성자의 수정/삭제 시도 | 다른 userId | 403 |
| SC-005 | 통합 테스트 (API) | 존재하지 않는 postId에 댓글 작성 | 잘못된 postId | 404 |
| SC-006 | 통합 테스트 (API) | 비작성자의 댓글 삭제 시도 | 다른 userId | 403 |
| SC-007 | 단위 테스트 (application) | 게시글 생성 직후 aiStatus 확인 | 정상 생성 | PENDING, tags=[], summary=null |
| SC-008 | 단위 테스트 (application) | EnrichPostUseCase 성공 경로 | AiTaggingPort Mock 성공 응답 | aiStatus=COMPLETED, tags/summary 채워짐 |
| SC-009 | 단위 테스트 (application) | EnrichPostUseCase 실패 경로 | AiTaggingPort Mock 예외 발생 | aiStatus=FAILED, 게시글 조회/수정/삭제는 영향 없음 |
| SC-010 | 통합 테스트 (API) | 존재하지 않는 postId 상세 조회 | 잘못된 postId | 404 |
| SC-011 | 통합 테스트 (API) | 댓글 2건이 달린 게시글의 댓글 목록 조회 | 댓글 2건 시드 | 2건 모두 반환 |

## 기타 고려사항

- **Claude API 호출 격리**: `ClaudeAiTaggingClient`는 인터페이스(`AiTaggingPort`) 뒤에 숨어 있으므로, 테스트에서는 실제 네트워크 호출이 발생하지 않는다. 실제 API 키를 이용한 수동 스모크 테스트는 로컬 개발 중 1회 수행을 권장한다 (자동화된 CI 테스트에는 포함하지 않음 — API 비용·플레이키니스 방지).
- **모델 업그레이드 경로**: `claude-haiku-4-5`로 시작하고, 태그 품질이 기대 이하일 경우 `application.yml`의 모델 ID 설정값만 `claude-sonnet-5`로 변경하면 되도록 모델 ID를 하드코딩하지 않고 설정값(`@Value` 또는 `@ConfigurationProperties`)으로 분리한다.
- **댓글 개수 표시**: 게시글 목록 응답에 댓글 개수를 포함할지는 이번 spec에서 다루지 않는다 (성능 이슈 없이 조회 시점에 `count` 쿼리로 계산 가능하나, 향후 캐싱 spec(003)과 함께 최적화 여지가 있어 범위에서 제외).
