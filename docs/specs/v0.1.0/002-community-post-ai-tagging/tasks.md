# Tasks: community-post-ai-tagging

> Branch: 002-community-post-ai-tagging | Date: 2026-07-09 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과.
- [ ] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 001 spec이 아직 구현되지 않아 CHANGES.md 없음. **001 구현 완료 후 002 착수 시 재확인 필요**.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. 의존 관계가 있는 태스크는 반드시 선행 태스크 완료 후 실행한다.
> 본 tasks.md는 001(class-enrollment-core) 구현 완료 후 착수를 전제로 작성되었다.

### Phase 1. 기반 작업

- [ ] **T001** — MongoDB 로컬 기동 구성 추가
  - 구현 파일: `docker-compose.yml`(수정), `src/main/resources/application.yml`(수정)
  - 관련 요구사항: 없음
  - 상세: 001의 MySQL 컨테이너와 병행하여 MongoDB 8 컨테이너 추가
  - 완료 기준: `docker compose up -d` 후 애플리케이션이 MongoDB 연결에 성공한다

- [ ] **T002** `[P]` — Gradle 의존성 추가
  - 구현 파일: `build.gradle.kts`
  - 상세: `spring-boot-starter-data-mongodb`, `com.anthropic:anthropic-java` 추가
  - 완료 기준: `./gradlew build` 성공

- [ ] **T003** — 공통 AI 연동 설정 (T002 완료 후)
  - 구현 파일: `common/AiEnrichmentConfig.kt`
  - 상세: `@EnableAsync`, Claude 클라이언트 빈(`AnthropicOkHttpClient.fromEnv()`), 모델 ID를 `application.yml`에서 주입받는 설정 프로퍼티(`@ConfigurationProperties`)
  - 완료 기준: 컴파일 성공, `ANTHROPIC_API_KEY` 미설정 시에도 애플리케이션 기동에는 영향 없음(호출 시점에만 필요)

### Phase 2. 핵심 구현 — Post

- [ ] **T004** — Post 도메인 모델 구현 (T001 완료 후)
  - 구현 파일: `post/domain/Post.kt`, `post/domain/PostAiStatus.kt`, `post/domain/exception/*.kt`
  - 관련 요구사항: `FR-001`, `FR-009`, `FR-010`, `FR-011`
  - 상세: `markEnrichmentCompleted()`/`markEnrichmentFailed()` 메서드로 AI 상태 전이 캡슐화
  - 완료 기준: 프레임워크 의존 없이 순수 Kotlin으로 컴파일된다

- [ ] **T005** — PostRepository + MongoDB 구현체 (T004 완료 후)
  - 구현 파일: `post/domain/PostRepository.kt`, `post/infrastructure/PostMongoDocument.kt`, `post/infrastructure/PostMongoRepository.kt`, `post/infrastructure/PostRepositoryImpl.kt`
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`
  - 완료 기준: Testcontainers 통합 테스트로 저장·조회 왕복 확인

- [ ] **T006** — AiTaggingPort + ClaudeAiTaggingClient 구현 (T003 완료 후)
  - 구현 파일: `post/domain/AiTaggingPort.kt`, `post/infrastructure/ClaudeAiTaggingClient.kt`
  - 관련 요구사항: `FR-009`
  - 상세: `StructuredMessageCreateParams` 기반 구조화된 출력(`PostEnrichmentResult(tags, summary)`), API 예외를 도메인 예외로 변환
  - 완료 기준: 단위 테스트에서 Claude API를 MockK로 대체해 정상/실패 응답 처리를 검증 (실제 네트워크 호출 없음)

- [ ] **T007** — Post 유스케이스 구현 (T005, T006 완료 후)
  - 구현 파일: `post/application/CreatePostUseCase.kt`, `GetPostUseCase.kt`, `ListPostsUseCase.kt`, `UpdatePostUseCase.kt`, `DeletePostUseCase.kt`, `EnrichPostUseCase.kt`
  - 관련 요구사항: `FR-001`~`FR-005`, `FR-009`~`FR-011`
  - 완료 기준: 각 유스케이스 단위 테스트(MockK) 통과

- [ ] **T008** — 비동기 이벤트 발행/구독 연결 (T007 완료 후)
  - 구현 파일: `post/infrastructure/PostCreatedEventListener.kt`, `post/domain/event/PostCreatedEvent.kt`
  - 관련 요구사항: `NFR-001`
  - 상세: `CreatePostUseCase`가 저장 직후 `PostCreatedEvent` 발행, `@Async` 리스너가 `EnrichPostUseCase` 호출
  - 완료 기준: 통합 테스트로 게시글 생성 응답이 AI 처리 완료를 기다리지 않음을 확인

- [ ] **T009** — PostController 및 DTO 구현 (T008 완료 후)
  - 구현 파일: `post/presentation/PostController.kt`, `post/presentation/dto/*.kt`
  - 관련 요구사항: `FR-001`~`FR-005`, `FR-010`
  - 완료 기준: MockMvc 슬라이스 테스트로 요청/응답 스키마 확인

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
