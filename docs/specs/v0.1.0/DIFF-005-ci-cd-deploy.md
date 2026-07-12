# Diff: 005-ci-cd-deploy

## 커밋 메시지 한 줄 요약

- **KO**: GitHub Actions CI(테스트 자동 실행 + 실패 시 병합/배포 차단)와 관리형 서비스(MongoDB Atlas/Upstash/Aiven/Render) 기반 무료 CD 파이프라인을 구축하고, SC-001~SC-006 전체를 실제 배포 환경에서 검증했다.
- **EN**: Set up a GitHub Actions CI pipeline (automated tests, merge/deploy blocking on failure) and a free CD pipeline on managed services (MongoDB Atlas/Upstash/Aiven/Render), verifying SC-001~SC-006 against the live deployment.

## 변경 요약

001~004에서 구현한 애플리케이션을 처음으로 GitHub Actions CI와 실제 배포 환경에 연결했다. `test`→`deploy`(`needs` 의존) 2-job 워크플로우로 테스트 실패 시 배포 자체가 실행되지 않도록 보장했으며, Render의 자체 Auto-Deploy는 꺼서 이 게이트를 우회하지 못하게 했다. 3개 저장소(MySQL·MongoDB·Redis)를 무료로 운영하기 위해 Aiven·MongoDB Atlas·Upstash 관리형 서비스를 각각 도입했고, `application.yml`의 연결 설정을 환경변수로 전체 오버라이드 가능하게 바꿔 로컬 개발 동작을 그대로 유지하면서 배포 환경에 대응했다. `domain` 계층을 포함한 애플리케이션 코드는 전혀 변경하지 않았다(순수 인프라 spec, `src/test` net diff 0 — 검증용으로 추가했던 임시 실패 테스트는 검증 직후 모두 제거됨). 브랜치 보호 규칙이 private 저장소에서 GitHub Pro를 요구해 저장소를 public으로 전환했으며, 전환 전 전체 git 히스토리에 실제 시크릿이 없음을 확인했다. SC-001~SC-006 전체를 실제 GitHub Actions 실행과 배포된 Render URL(`https://class-platform-quan.onrender.com`)에 대한 라이브 검증으로 통과시켰다(SC-004는 main에 실패 테스트를 직접 push해 배포가 스킵됨을 확인한 뒤 즉시 되돌리는 방식으로 검증).

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `.dockerignore` | +41 | -0 |
| `.env.example` | +22 | -0 |
| `.github/workflows/ci-cd.yml` | +27 | -0 |
| `.gitignore` | +1 | -0 |
| `Dockerfile` | +13 | -0 |
| `build.gradle.kts` | +1 | -0 |
| `src/main/resources/application.yml` | +17 | -2 |

**합계**: 7 files changed, 122 insertions(+), 2 deletions(-) (spec 문서 자체 변경분 제외, `src/test` 검증용 임시 파일은 순변화 0으로 제외)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(004 완료 직후, 005 설계 착수 직전)과 재생성 명령만 기록한다.

- base commit: `e8eb71c` ([docs] 004-complex-query-statistics 구현 완료 기록 및 context/infra 현행화)
- 재생성 명령: `git diff e8eb71c HEAD -- src/main build.gradle.kts Dockerfile .dockerignore .github .env.example .gitignore`
