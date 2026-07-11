# Tasks: community-post-ai-tagging

> Branch: 002-community-post-ai-tagging | Date: 2026-07-09 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 001 구현 완료. FR-008 소유자 권한 미구현, X-User-Id 기반 임시 인증, "상태 기반 거부는 409" 관례, description HTML sanitize(`HtmlSanitizer`) 재사용 항목을 확인했다. 002의 Post/Comment도 사용자 입력 HTML을 저장하므로 동일하게 `HtmlSanitizer`를 재사용할 예정이다.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. 의존 관계가 있는 태스크는 반드시 선행 태스크 완료 후 실행한다.
> 본 tasks.md는 001(class-enrollment-core) 구현 완료 후 착수를 전제로 작성되었다.

### Phase 1. 기반 작업

- [x] **T001** — MongoDB 로컬 기동 구성 추가
  - 구현 파일: `docker-compose.yml`(수정), `src/main/resources/application.yml`(수정)
  - 관련 요구사항: 없음
  - 상세: 001의 MySQL 컨테이너와 병행하여 MongoDB 8 컨테이너 추가
  - 완료 기준: `docker compose up -d` 후 애플리케이션이 MongoDB 연결에 성공한다

- [x] **T002** `[P]` — Gradle 의존성 추가
  - 구현 파일: `build.gradle.kts`
  - 상세: `spring-boot-starter-data-mongodb`, `com.anthropic:anthropic-java:2.34.0` 추가. T005 통합 테스트에 필요한 `org.testcontainers:mongodb:1.20.1`(testImplementation)도 함께 추가
  - 완료 기준: `./gradlew build` 성공 — 확인 완료. 추가로 앱 기동 시 MongoDB 연결 성공(`state=CONNECTED`) 로그로 T001의 완료 기준까지 함께 검증함

- [x] **T003** — 공통 AI 연동 설정 (T002 완료 후)
  - 구현 파일: `common/AiEnrichmentConfig.kt`
  - 상세: `@EnableAsync`, Claude 클라이언트 빈(`AnthropicOkHttpClient.fromEnv()`), 모델 ID를 `application.yml`에서 주입받는 설정 프로퍼티(`@ConfigurationProperties`, prefix `ai-enrichment.model`, 기본값 `claude-haiku-4-5`)
  - 완료 기준: 컴파일 성공 확인. `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN`/`ANTHROPIC_PROFILE` 전부 unset한 셸에서 `./gradlew bootRun` 정상 기동 확인 (`AnthropicOkHttpClient.fromEnv()`는 빈 생성 시점에 자격증명을 검증하지 않고 실제 API 호출 시점에만 필요)

### Phase 2. 핵심 구현 — Post

- [x] **T004** — Post 도메인 모델 구현 (T001 완료 후)
  - 구현 파일: `post/domain/Post.kt`, `post/domain/PostAiStatus.kt`, `post/domain/exception/{PostNotFoundException, PostAccessDeniedException}.kt`
  - 관련 요구사항: `FR-001`, `FR-009`, `FR-010`, `FR-011`
  - 상세: `markEnrichmentCompleted()`/`markEnrichmentFailed()` 메서드로 AI 상태 전이 캡슐화. `updateContent()`도 함께 추가(FR-004, T007 UpdatePostUseCase에서 사용 예정) — title/body 불변식을 도메인 한 곳에서만 검증하기 위함
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다 — 확인 완료 (`post/domain/` import가 `com.classplatform.common`과 JDK 표준 라이브러리뿐임을 grep으로 검증)

- [x] **T005** — PostRepository + MongoDB 구현체 (T004 완료 후)
  - 구현 파일: `post/domain/PostRepository.kt`, `post/infrastructure/PostMongoDocument.kt`, `post/infrastructure/PostMongoRepository.kt`, `post/infrastructure/PostRepositoryImpl.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 완료 기준: Testcontainers 통합 테스트(`PostRepositoryImplIT`)로 저장·조회 왕복 + 최신순 정렬 확인 — 통과
  - **구현 중 발견한 이슈 1**: Mongo `save()`는 문서 전체를 덮어쓰므로(JPA의 컬럼 단위 UPDATE와 다름), 수정 시 새 transient document를 만들면 `createdAt`이 null로 덮어써진다. `PostRepositoryImpl.save()`에서 id가 있으면 기존 문서의 `createdAt`을 먼저 조회해 보존하도록 처리(001의 JPA merge 이슈와 같은 계열의 버그).
  - **구현 중 발견한 이슈 2**: `@EnableMongoAuditing`을 `ClassPlatformApplication`(메인 `@SpringBootApplication` 클래스)에 직접 붙였더니, `@WebMvcTest` 등 Mongo와 무관한 슬라이스 테스트에서도 이 애노테이션이 그대로 적용되어 `mongoMappingContext` 빈 부재로 14개 테스트가 컨텍스트 로딩 실패했다. `common/config/MongoAuditingConfig.kt`(별도 `@Configuration`)로 분리하고, `@DataMongoTest` 슬라이스 테스트에서는 `@Import`로 명시적으로 가져오도록 수정해 해결.
  - **테스트 격리 이슈**: `@DataMongoTest`는 `@DataJpaTest`와 달리 기본적으로 트랜잭션 롤백이 되지 않아, 테스트 메서드 간 데이터가 누적된다. `@BeforeEach`에서 `mongoRepository.deleteAll()`로 정리.

- [x] **T006** — AiTaggingPort + ClaudeAiTaggingClient 구현 (T003 완료 후)
  - 구현 파일: `post/domain/AiTaggingPort.kt`, `post/domain/exception/AiTaggingFailedException.kt`, `post/infrastructure/ClaudeAiTaggingClient.kt`
  - 관련 요구사항: `FR-009`
  - 상세: `StructuredMessageCreateParams` 기반 구조화된 출력(`PostEnrichmentResult(tags, summary)`), API 예외를 도메인 예외로 변환
  - 완료 기준: 단위 테스트에서 Claude API를 MockK로 대체해 정상/실패 응답 처리를 검증 (실제 네트워크 호출 없음) — `ClaudeAiTaggingClientTest` 2건 통과
  - **구현 노트**: `AiTaggingPort`는 순수 도메인 인터페이스(`generateTagsAndSummary(title, body): AiEnrichmentResult`)로 SDK 타입에 의존하지 않는다. 실제 API 호출·구조화 스키마(`PostEnrichmentResult`)·예외 변환(`AnthropicException` 하위 전체 → `AiTaggingFailedException`)은 infrastructure의 `ClaudeAiTaggingClient`에 캡슐화했다. AI 응답이 태그 개수·요약 길이 제약을 어기는 경우의 방어는 별도 구현하지 않았다 — `Post.markEnrichmentCompleted()`(T004)가 이미 도메인 불변식으로 검증하므로 중복을 피하고, T007의 `EnrichPostUseCase`가 그 예외를 잡아 FAILED로 전이시키는 흐름에 위임한다.
  - **테스트 기법 노트**: `anthropic-java` SDK의 `StructuredMessage`/`StructuredContentBlock`/`StructuredTextBlock`은 `internal constructor`를 가진 제네릭 클래스이지만, MockK(1.13.12, 인라인 mock agent 내장)는 Objenesis 기반으로 생성자 호출 없이 프록시를 만들기 때문에 체이닝 목킹(`content().first().asText().text()`)이 문제없이 동작했다. 목킹을 위해 `PostEnrichmentResult`는 `private`이 아닌 `internal` 가시성으로 선언했다(테스트 소스셋은 Kotlin Gradle 플러그인이 기본으로 main과 friend path를 공유하므로 `internal` 접근 가능).

- [x] **T007** — Post 유스케이스 구현 (T005, T006 완료 후)
  - 구현 파일: `post/application/CreatePostUseCase.kt`, `GetPostUseCase.kt`, `ListPostsUseCase.kt`, `UpdatePostUseCase.kt`, `DeletePostUseCase.kt`, `EnrichPostUseCase.kt`
  - 관련 요구사항: `FR-001`~`FR-005`, `FR-009`~`FR-011`
  - 완료 기준: 각 유스케이스 단위 테스트(MockK) 통과 — 6개 파일 14건 통과
  - **구현 노트**: `CreatePostUseCase`/`UpdatePostUseCase`는 001에서 확립한 관례대로 본문(`body`)을 `HtmlSanitizer`로 sanitize한다(title은 Course와 동일하게 sanitize 대상에서 제외). `PostRepository`에 `deleteById(id)`를 추가했다(`PostRepositoryImpl`은 `MongoRepository.deleteById` 위임). `EnrichPostUseCase`는 아직 어디에서도 호출되지 않는 독립 유스케이스로, PostCreatedEvent 리스너(T008)가 연결한다. AI 태깅 실패 처리는 `AiTaggingFailedException`뿐 아니라 `Exception` 전체를 잡아 FAILED로 흡수한다 — AI가 태그 5개·요약 200자 불변식을 어겨 `Post.markEnrichmentCompleted()`(T004)가 던지는 경우까지 게시글 자체에는 영향이 없어야 하기 때문(NFR-004 장애 격리).

- [x] **T008** — 비동기 이벤트 발행/구독 연결 (T007 완료 후)
  - 구현 파일: `post/infrastructure/PostCreatedEventListener.kt`, `post/domain/event/PostCreatedEvent.kt`
  - 관련 요구사항: `NFR-001`
  - 상세: `CreatePostUseCase`가 저장 직후 `PostCreatedEvent` 발행, `@Async` 리스너가 `EnrichPostUseCase` 호출
  - 완료 기준: 통합 테스트로 게시글 생성 응답이 AI 처리 완료를 기다리지 않음을 확인 — `PostCreatedEventIT` 통과
  - **구현 노트**: `CreatePostUseCase`는 `ApplicationEventPublisher`를 주입받아 `postRepository.save()` 직후 `PostCreatedEvent(postId, title, body)`를 발행하도록 수정했다(T007에서 만든 시그니처 변경, 기존 단위 테스트도 `eventPublisher` mock 추가로 갱신). `PostCreatedEventListener`는 `@Async` + `@EventListener`로 `EnrichPostUseCase`를 호출하며, `@EnableAsync`는 T003의 `AiEnrichmentConfig`가 이미 전역 적용 중이라 별도 설정이 필요 없었다.
  - **테스트 설계 노트**: 완료 기준 검증은 HTTP 계층(T009 Controller)이 아직 없어 `PostControllerIT` 대신 `@SpringBootTest` 풀 컨텍스트로 `CreatePostUseCase`를 직접 호출하는 `PostCreatedEventIT`를 작성했다. `@MockkBean`으로 `AiTaggingPort`를 1초 지연 응답으로 교체해, (1) `CreatePostUseCase.execute()`가 500ms 이내로 즉시 반환되는지(NFR-001), (2) 그 후 비동기로 `aiStatus`가 실제로 PENDING→COMPLETED까지 전이되는지(수동 폴링, 3초 타임아웃)를 함께 검증했다. 풀 컨텍스트라 MySQL·MongoDB Testcontainers를 모두 띄운다(001의 JPA/Flyway + 002의 Mongo가 같은 `ClassPlatformApplication`에 공존하기 때문).

- [x] **T009** — PostController 및 DTO 구현 (T008 완료 후)
  - 구현 파일: `post/presentation/PostController.kt`, `post/presentation/dto/*.kt`
  - 관련 요구사항: `FR-001`~`FR-005`, `FR-010`
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인 — `PostControllerTest` 9건 통과
  - **구현 노트**: 001의 `CourseController`/`ApiResponse` 관례를 그대로 따랐다(`X-User-Id` 헤더, `ApiResponse<T>` 래핑, POST 201/DELETE 204). plan.md의 PATCH 계약(`{title?, body?}` 부분 수정)을 지원하기 위해, 컨트롤러에서 기존 값을 다시 조회해 병합하는 대신 T007에서 만든 `UpdatePostUseCase.execute(postId, title: String, body: String, ...)`를 `title: String?, body: String?`로 변경해 병합 로직을 유스케이스 내부로 옮겼다(불필요한 이중 조회 회피, 기존 `UpdatePostUseCaseTest`에 부분 수정 케이스 1건 추가). `PostControllerTest` 작성 시 001의 `CourseControllerTest`에서 이미 확인된 주의사항(`UserId` value class 파라미터에 MockK `any()`를 쓰면 내부에서 생성한 임의 값이 `require(value > 0)`에 걸려 무작위로 실패할 수 있음)을 그대로 적용해 해당 파라미터는 명시적으로 매칭했다.

### Phase 3. 핵심 구현 — Comment

- [ ] **T010** — Comment 도메인 모델 구현 (T001 완료 후, T004와 병렬 가능) `[P]`
  - 구현 파일: `comment/domain/Comment.kt`, `comment/domain/exception/*.kt`
  - 관련 요구사항: `FR-006`, `FR-007`, `FR-008`
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다

- [ ] **T011** — CommentRepository + MongoDB 구현체 (T010 완료 후)
  - 구현 파일: `comment/domain/CommentRepository.kt`, `comment/infrastructure/CommentMongoDocument.kt`, `comment/infrastructure/CommentMongoRepository.kt`, `comment/infrastructure/CommentRepositoryImpl.kt`
  - 완료 기준: Testcontainers 통합 테스트로 저장·조회 왕복 확인

- [ ] **T012** — Comment 유스케이스 구현 (T011, T005 완료 후)
  - 구현 파일: `comment/application/CreateCommentUseCase.kt`, `ListCommentsUseCase.kt`, `DeleteCommentUseCase.kt`
  - 관련 요구사항: `FR-006`, `FR-007`, `FR-008`
  - 상세: `CreateCommentUseCase`는 `PostRepository`로 게시글 존재 여부를 확인한다
  - 완료 기준: 각 유스케이스 단위 테스트(MockK) 통과

- [ ] **T013** — CommentController 및 DTO 구현 (T012 완료 후)
  - 구현 파일: `comment/presentation/CommentController.kt`, `comment/presentation/dto/*.kt`
  - 관련 요구사항: `FR-006`, `FR-007`, `FR-008`
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

### Phase 4. 테스트 (SC-XXX 검증)

- [ ] **T014** `[P]` — SC-001 도메인 단위 테스트
  - 테스트 파일: `post/domain/PostTest.kt`
  - 검증 대상: `SC-001`

- [ ] **T015** `[P]` — SC-007, SC-008, SC-009 유스케이스 단위 테스트
  - 테스트 파일: `post/application/EnrichPostUseCaseTest.kt`
  - 검증 대상: `SC-007`, `SC-008`, `SC-009`
  - 시나리오: AiTaggingPort Mock 성공/실패 응답에 따른 aiStatus 전이 확인

- [ ] **T016** — SC-002, SC-003, SC-010 통합 테스트 (T009 완료 후)
  - 테스트 파일: `post/presentation/PostControllerIT.kt`
  - 검증 대상: `SC-002`, `SC-003`, `SC-010`

- [ ] **T017** — SC-004 통합 테스트 (T009 완료 후)
  - 테스트 파일: `post/presentation/PostAuthorizationIT.kt`
  - 검증 대상: `SC-004`

- [ ] **T018** — SC-005, SC-006, SC-011 통합 테스트 (T013 완료 후)
  - 테스트 파일: `comment/presentation/CommentControllerIT.kt`
  - 검증 대상: `SC-005`, `SC-006`, `SC-011`

## 구현 완료 기준

- [ ] 모든 태스크 체크박스가 완료 처리되었다.
- [ ] `./gradlew test`가 전체 PASSED를 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다.
