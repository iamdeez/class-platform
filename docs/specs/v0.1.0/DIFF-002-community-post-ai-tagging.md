# Diff: 002-community-post-ai-tagging

## 커밋 메시지 한 줄 요약

- **KO**: 커뮤니티 게시글·댓글 도메인을 MongoDB 기반으로 구현하고, Claude API(Structured Outputs)로 게시글 등록 후 비동기 AI 태깅(카테고리·요약)을 붙였다. SC-001~SC-011 전체를 단위·슬라이스·통합(Testcontainers) 테스트로 검증했다.
- **EN**: Implement the community post/comment domain on MongoDB, and attach asynchronous AI tagging (Claude API Structured Outputs) after post creation. Verify SC-001~SC-011 via unit, slice, and Testcontainers integration tests.

## 변경 요약

001의 MySQL 단일 의존 구조에 MongoDB를 병행 도입해 `post`/`comment` 두 애그리거트를 4계층(도메인/인프라/애플리케이션/프레젠테이션)으로 구현했다. `Post`는 등록 직후 `PostCreatedEvent`를 발행하고, `@Async` 리스너가 `EnrichPostUseCase`를 통해 `anthropic-java` SDK(Structured Outputs)로 카테고리 태그·요약을 비동기 생성한다(NFR-001 응답 지연 없음, NFR-004 장애 격리). 도메인 계층은 001과 동일하게 Spring·MongoDB·Claude SDK 어떤 것에도 의존하지 않는 순수 Kotlin으로 유지했다(`AiTaggingPort` 인터페이스로 SDK를 격리). `Comment`는 수정 기능이 스펙 범위 외라 mutation 없는 값 객체로 단순화했다. spec.md의 수용 기준(SC-001~SC-011) 전체가 최소 1개 이상의 테스트로 매핑되어 있다.

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `.env.example` | +9 | -0 |
| `build.gradle.kts` | +3 | -0 |
| `docker-compose.yml` | +19 | -0 |
| `src/main/resources/application.yml` | +6 | -0 |
| `src/main/kotlin/com/classplatform/common/AiEnrichmentConfig.kt` | +23 | -0 |
| `src/main/kotlin/com/classplatform/common/config/MongoAuditingConfig.kt` | +8 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/Post.kt` | +73 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostAiStatus.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/PostRepository.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/AiTaggingPort.kt` | +10 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/event/PostCreatedEvent.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/exception/PostNotFoundException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/exception/PostAccessDeniedException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/post/domain/exception/AiTaggingFailedException.kt` | +3 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostMongoDocument.kt` | +26 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostMongoRepository.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostRepositoryImpl.kt` | +68 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/ClaudeAiTaggingClient.kt` | +54 | -0 |
| `src/main/kotlin/com/classplatform/post/infrastructure/PostCreatedEventListener.kt` | +18 | -0 |
| `src/main/kotlin/com/classplatform/post/application/CreatePostUseCase.kt` | +22 | -0 |
| `src/main/kotlin/com/classplatform/post/application/GetPostUseCase.kt` | +14 | -0 |
| `src/main/kotlin/com/classplatform/post/application/ListPostsUseCase.kt` | +15 | -0 |
| `src/main/kotlin/com/classplatform/post/application/UpdatePostUseCase.kt` | +26 | -0 |
| `src/main/kotlin/com/classplatform/post/application/DeletePostUseCase.kt` | +20 | -0 |
| `src/main/kotlin/com/classplatform/post/application/EnrichPostUseCase.kt` | +28 | -0 |
| `src/main/kotlin/com/classplatform/post/presentation/PostController.kt` | +102 | -0 |
| `src/main/kotlin/com/classplatform/post/presentation/dto/PostDtos.kt` | +33 | -0 |
| `src/main/kotlin/com/classplatform/comment/domain/Comment.kt` | +22 | -0 |
| `src/main/kotlin/com/classplatform/comment/domain/CommentRepository.kt` | +11 | -0 |
| `src/main/kotlin/com/classplatform/comment/domain/exception/CommentNotFoundException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/comment/domain/exception/CommentAccessDeniedException.kt` | +5 | -0 |
| `src/main/kotlin/com/classplatform/comment/infrastructure/CommentMongoDocument.kt` | +18 | -0 |
| `src/main/kotlin/com/classplatform/comment/infrastructure/CommentMongoRepository.kt` | +7 | -0 |
| `src/main/kotlin/com/classplatform/comment/infrastructure/CommentRepositoryImpl.kt` | +41 | -0 |
| `src/main/kotlin/com/classplatform/comment/application/CreateCommentUseCase.kt` | +21 | -0 |
| `src/main/kotlin/com/classplatform/comment/application/ListCommentsUseCase.kt` | +12 | -0 |
| `src/main/kotlin/com/classplatform/comment/application/DeleteCommentUseCase.kt` | +21 | -0 |
| `src/main/kotlin/com/classplatform/comment/presentation/CommentController.kt` | +63 | -0 |
| `src/main/kotlin/com/classplatform/comment/presentation/dto/CommentDtos.kt` | +21 | -0 |
| `src/test/kotlin/com/classplatform/post/domain/PostTest.kt` | +29 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/ClaudeAiTaggingClientTest.kt` | +54 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/PostRepositoryImplIT.kt` | +80 | -0 |
| `src/test/kotlin/com/classplatform/post/infrastructure/PostCreatedEventIT.kt` | +80 | -0 |
| `src/test/kotlin/com/classplatform/post/application/CreatePostUseCaseTest.kt` | +73 | -0 |
| `src/test/kotlin/com/classplatform/post/application/GetPostUseCaseTest.kt` | +43 | -0 |
| `src/test/kotlin/com/classplatform/post/application/ListPostsUseCaseTest.kt` | +38 | -0 |
| `src/test/kotlin/com/classplatform/post/application/UpdatePostUseCaseTest.kt` | +68 | -0 |
| `src/test/kotlin/com/classplatform/post/application/DeletePostUseCaseTest.kt` | +55 | -0 |
| `src/test/kotlin/com/classplatform/post/application/EnrichPostUseCaseTest.kt` | +73 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostControllerTest.kt` | +157 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostControllerIT.kt` | +114 | -0 |
| `src/test/kotlin/com/classplatform/post/presentation/PostAuthorizationIT.kt` | +82 | -0 |
| `src/test/kotlin/com/classplatform/comment/infrastructure/CommentRepositoryImplIT.kt` | +82 | -0 |
| `src/test/kotlin/com/classplatform/comment/application/CreateCommentUseCaseTest.kt` | +63 | -0 |
| `src/test/kotlin/com/classplatform/comment/application/ListCommentsUseCaseTest.kt` | +28 | -0 |
| `src/test/kotlin/com/classplatform/comment/application/DeleteCommentUseCaseTest.kt` | +47 | -0 |
| `src/test/kotlin/com/classplatform/comment/presentation/CommentControllerTest.kt` | +108 | -0 |
| `src/test/kotlin/com/classplatform/comment/presentation/CommentControllerIT.kt` | +107 | -0 |

**합계**: 58 files changed, 2251 insertions(+)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(001 후속 보완 직후, 002 구현 착수 직전)과 재생성 명령만 기록한다.

- base commit: `01c4c21` ([feat] Course description HTML sanitize 추가 + springdoc-openapi 연동)
- 재생성 명령: `git diff 01c4c21 -- src/main/kotlin/com/classplatform/post src/main/kotlin/com/classplatform/comment src/main/kotlin/com/classplatform/common/AiEnrichmentConfig.kt src/main/kotlin/com/classplatform/common/config/MongoAuditingConfig.kt src/test/kotlin/com/classplatform/post src/test/kotlin/com/classplatform/comment build.gradle.kts docker-compose.yml .env.example src/main/resources/application.yml`
