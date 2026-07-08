# Research: community-post-ai-tagging

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

본 spec 작성 시점까지 `class-platform`에는 소스 코드가 없다 (001 spec은 설계만 완료, 구현 착수 전). 따라서 001의 `course`/`enrollment` 패키지와의 직접적인 코드 의존은 없으나, `common` 패키지(`UserId` 값 객체)는 001에서 설계한 대로 재사용한다 (`docs/specs/v0.1.0/001-class-enrollment-core/research.md` 패키지 구조 참조).

## 기술 선택 조사

### 영속성 계층

- **MongoDB**: 001 research.md에서 이미 "게시글·댓글은 로그성/비정형 콘텐츠 데이터이므로 MongoDB를 사용한다"고 결정했다. 본 spec은 그 결정을 그대로 따른다. `posts`, `comments` 두 개의 컬렉션으로 분리한다 (댓글을 게시글 문서에 임베딩하지 않음 — 인기 게시글의 댓글 수가 늘어날수록 문서 크기 제한(16MB) 및 쓰기 경합 위험이 커지므로 별도 컬렉션 + `postId` 참조 방식을 채택).

### AI 태깅·요약 연동

- **선택**: Anthropic Claude API (Messages API)를 사용한다. 채용공고의 "AI Dev Tools: Claude Code, Cursor, Copilot, ChatGPT 등" 언급 및 AX 경험 접목이라는 본 spec의 목적에 부합한다.
- **모델 선택**: `claude-haiku-4-5` — 카테고리 태깅·요약은 단순 분류·요약 작업이라 최상위 모델(Opus/Sonnet)의 추론 능력이 필요하지 않으며, Haiku 4.5가 속도·비용(입력 $1/출력 $5 per 1M 토큰) 대비 충분한 품질을 낸다. 실제 구현 후 태그 품질이 기대에 못 미치면 `claude-sonnet-5`로 승급을 고려한다 (모델 ID 문자열만 교체하면 되므로 전환 비용은 낮다).
- **SDK 선택**: JVM 공식 SDK인 `com.anthropic:anthropic-java`를 사용한다 (Kotlin은 Java SDK를 그대로 사용). 순수 HTTP(WebClient/RestClient) 직접 호출은 사용하지 않는다 — 공식 SDK가 요청/응답 타입, 재시도(기본 2회, 429/5xx 대상), 타임아웃, 구조화된 출력 파싱을 이미 제공하기 때문이다.
  - Gradle 의존성: `implementation("com.anthropic:anthropic-java:<latest>")`
  - 클라이언트 초기화: `AnthropicOkHttpClient.fromEnv()` — `ANTHROPIC_API_KEY` 환경변수를 자동으로 읽어 인증하므로 코드에 키를 하드코딩할 지점 자체가 없다.
- **구조화된 JSON 출력**: `tool_choice` 강제 방식 대신 **Structured Outputs**(`output_config.format`)를 사용한다. 카테고리 태그·요약을 담는 Kotlin data class(`PostEnrichmentResult(val tags: List<String>, val summary: String)`)를 정의하고, Java SDK의 `StructuredMessageCreateParams` 클래스 기반 오버로드(`.outputConfig(PostEnrichmentResult::class.java)`)로 스키마를 자동 유도한다. 이 방식은 apply 시점에 JSON 파싱 실패 가능성을 원천 차단한다 (모델 응답이 스키마를 만족함이 API 레벨에서 보장됨).
- **연동 방식**: 게시글 제목·본문을 프롬프트에 담아 위 구조화된 응답으로 카테고리 태그(최대 5개)와 요약(최대 200자)을 받는다. API 오류(레이트리밋, 타임아웃 등)는 SDK의 타입 예외(`RateLimitException`, `AnthropicIoException` 등)를 캐치해 도메인 예외로 변환한다.
- **포트-어댑터 분리**: 도메인(`post/domain`)은 `AiTaggingPort` 인터페이스(추상화)만 알고, 실제 Claude API 호출(공식 SDK 사용)은 `post/infrastructure`의 어댑터(`ClaudeAiTaggingClient`)가 담당한다 (constitution P-001 도메인 순수성 원칙 준수).
- **API 키 관리**: 환경변수 `ANTHROPIC_API_KEY`로 주입하며 코드·설정 파일에 하드코딩하지 않는다. 로컬 개발은 `.env`(git 추적 제외) + Docker Compose `env_file`로, 배포 환경은 시크릿 관리 도구로 주입한다고 가정한다. `infra.md`는 002 구현 완료 후 이 환경변수 요구사항을 반영해 갱신한다.

### 비동기 처리 방식

- **선택**: Spring의 `ApplicationEventPublisher` + `@Async` `@EventListener` 조합을 사용한다. 게시글 저장 트랜잭션 커밋 후(`@TransactionalEventListener(phase = AFTER_COMMIT)`) `PostCreatedEvent`를 발행하고, 별도 스레드에서 AI 태깅·요약 유스케이스를 실행한다.
- **대안 비교**: Kafka/RabbitMQ 등 메시지 브로커는 재시작 시에도 이벤트가 유실되지 않는 장점이 있으나, 001~003 로드맵 어디에도 메시지 브로커 도입 계획이 없고 이 시점에 도입하면 인프라 복잡도가 급격히 커진다. 학습 단계에 맞춰 인프로세스 비동기(`@Async`)로 시작하고, 유실 가능성은 "엣지 케이스 및 한계"에 명시한다.

### 패키지 구조 (Bounded Context 단위)

```
com.classplatform
├── post/                      ← 게시글 Bounded Context
│   ├── domain/                ← Post, PostAiStatus(PENDING/COMPLETED/FAILED), AiTaggingPort(인터페이스), 도메인 예외
│   ├── application/           ← CreatePostUseCase, GetPostUseCase, ListPostsUseCase, UpdatePostUseCase, DeletePostUseCase, EnrichPostUseCase
│   ├── infrastructure/        ← PostMongoDocument, PostMongoRepository(Spring Data MongoDB), PostRepositoryImpl, ClaudeAiTaggingClient(AiTaggingPort 구현체), PostCreatedEventListener
│   └── presentation/          ← PostController, 요청/응답 DTO
├── comment/                   ← 댓글 Bounded Context
│   ├── domain/                ← Comment, CommentRepository(인터페이스), 도메인 예외
│   ├── application/           ← CreateCommentUseCase, ListCommentsUseCase, DeleteCommentUseCase
│   ├── infrastructure/        ← CommentMongoDocument, CommentMongoRepository, CommentRepositoryImpl
│   └── presentation/          ← CommentController, 요청/응답 DTO
└── common/                    ← (001에서 정의한 UserId 재사용)
```

- `comment` 모듈은 `post` 모듈의 `PostRepository`(존재 여부 확인용, 조회 전용)에 의존한다 — FR-006(존재하지 않는 게시글에 댓글 작성 불가) 때문. 역방향 의존은 두지 않는다.
- `EnrichPostUseCase`는 `AiTaggingPort`(도메인 인터페이스)를 통해 태깅을 요청하고, 결과를 `PostRepository`로 갱신한다.

## 엣지 케이스 및 한계

- **인프로세스 비동기의 유실 가능성**: 애플리케이션이 `PostCreatedEvent` 처리 중(AI 호출 대기 중) 재시작되면 해당 이벤트는 유실되고, 해당 게시글은 PENDING 상태로 영구히 남는다. 001 로드맵에 메시지 브로커 도입 계획이 없으므로 이번 spec에서는 이 한계를 감수하고, 재시도/복구 메커니즘은 "범위 외"로 명시한다 (spec.md 참조).
- **AI 응답 형식 불일치**: Claude API가 예상한 JSON 스키마와 다른 형식으로 응답하는 경우, 파싱 실패로 처리하고 FAILED 상태로 전이한다 (재시도하지 않음).
- **동시 수정 경합**: 사용자가 게시글을 수정(FR-004)하는 시점과 AI 처리 완료 갱신(FR-010)이 동시에 발생할 수 있다. MongoDB는 단일 문서 갱신이 원자적이므로, 본문 필드(제목·본문)와 AI 결과 필드(태그·요약·상태)를 각각 별도의 `$set` 연산으로 갱신해 서로의 필드를 덮어쓰지 않도록 한다.
- **댓글 삭제 후 게시글 삭제 순서**: 게시글 삭제(FR-005) 시 해당 게시글의 댓글도 함께 삭제할지는 이번 spec에서 "함께 삭제"로 확정한다 (고아 댓글 방지). 대량 댓글 삭제 성능은 이 연습 프로젝트 규모에서 문제되지 않는다고 가정한다.
