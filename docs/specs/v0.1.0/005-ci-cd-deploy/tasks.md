# Tasks: ci-cd-deploy

> Branch: 005-ci-cd-deploy | Date: 2026-07-12 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과, 예외 없음.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 004까지는 순수 기능 spec이라 CI/CD·배포 관련 이전 이력 없음.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. `[수동]` 표시: AI가 대행할 수 없는 사용자 작업(웹 UI 계정·설정 조작).

### Phase 1. 코드 변경

- [x] **T001** — `spring-boot-starter-actuator` 의존성 추가
  - 구현 파일: `build.gradle.kts`
  - 관련 요구사항: `FR-005`
  - 상세: `/actuator/health` 엔드포인트 노출을 위한 의존성 추가
  - 완료 기준: `./gradlew build` 성공, 로컬 기동 후 `curl localhost:8080/actuator/health`가 200 반환 — 확인 완료 (`{"status":"UP"}`, HTTP 200. 기본 설정만으로 `/actuator` 하위 1개 엔드포인트(health)만 노출됨을 로그로 확인 — Spring Boot Actuator 기본값이 이미 안전한 최소 노출)

- [x] **T002** `[P]` — `application.yml` 배포 대응 설정 변경
  - 구현 파일: `src/main/resources/application.yml`
  - 관련 요구사항: `FR-005`, `FR-006`
  - 상세: `server.port`(`PORT` 환경변수 우선), `spring.datasource.url`/`spring.data.mongodb.uri` 전체 오버라이드 가능화(기존 로컬 기본값 유지), `spring.data.redis.password`/`ssl.enabled` 추가, `management.endpoints.web.exposure.include: health` + `show-details: never`
  - 완료 기준: 환경변수 미설정 시 기존 로컬 개발 동작(001~004의 모든 테스트)이 그대로 통과한다 — 확인 완료. `./gradlew bootRun`으로 중첩 플레이스홀더(`${MYSQL_JDBC_URL:jdbc:mysql://...${MYSQL_DATABASE:...}...}`) 정상 해석 확인(헬스체크 200), 전체 회귀 테스트(`BUILD SUCCESSFUL`, 실패 0건) 통과

- [x] **T003** `[P]` — `Dockerfile` 작성
  - 구현 파일: `Dockerfile`(신규)
  - 관련 요구사항: `FR-003`, `FR-005`
  - 상세: 멀티스테이지 빌드(`eclipse-temurin:17-jdk-alpine`으로 `./gradlew bootJar -x test` → `eclipse-temurin:17-jre-alpine`으로 실행)
  - 완료 기준: `docker build -t class-platform .` 성공, `docker run -p 8080:8080 class-platform`으로 로컬 기동 확인(연결 가능한 로컬 MySQL/MongoDB/Redis 필요 — `docker-compose up -d`와 병행) — 확인 완료. `docker run`에 `host.docker.internal`로 로컬 MySQL/MongoDB/Redis 연결 오버라이드하여 헬스체크 200 확인
  - **구현 중 발견한 이슈**: `eclipse-temurin:17-jre-alpine`은 amd64 아키텍처만 매니페스트에 존재해(arm64 없음), 로컬 Mac(arm64)에서 `docker build` 시 "no match for platform in manifest" 오류가 발생했다. Render 배포 대상이 어차피 x86_64이므로 두 스테이지 모두 `FROM --platform=linux/amd64`를 명시해 해결(로컬에서는 에뮬레이션으로 빌드/실행, 프로덕션과 동일 아키텍처 보장). buildkit이 "FromPlatformFlagConstDisallowed" 린트 경고를 내지만 빌드 실패는 아니며, 고정 아키텍처가 의도된 선택이라 허용했다

- [x] **T004** `[P]` — `.dockerignore` 작성
  - 구현 파일: `.dockerignore`(신규)
  - 상세: `~/.claude/rules/on-demand/docker.md` 표준 블록 적용(`.git/`, `.claude/*`(docs 화이트리스트 제외), `build/`, `.gradle/` 등)
  - 완료 기준: `docker build` 컨텍스트에 불필요한 파일이 포함되지 않는다(`docker build` 로그의 "Sending build context" 크기로 확인) — 확인 완료. 표준 블록에 프로젝트 특성(Gradle `.gradle/`/`build/`, spec 문서 `docs/`, 로컬 전용 `docker-compose.yml`/`.env`)을 추가. 재빌드 시 build context 774B(+ COPY 대상 20.54kB)로 최소화됨을 로그로 확인

- [ ] **T005** (T001, T002 완료 후) — CI/CD 워크플로우 작성
  - 구현 파일: `.github/workflows/ci-cd.yml`(신규)
  - 관련 요구사항: `FR-001`, `FR-002`, `FR-003`, `FR-004`
  - 상세: `test` job(push 전체 브랜치 + PR to main에서 `./gradlew test`) + `deploy` job(`needs: test`, main push에서만 `RENDER_DEPLOY_HOOK_URL` 호출)
  - 완료 기준: 워크플로우 파일이 `actionlint` 또는 GitHub Actions 문법 검사를 통과한다(YAML 문법 오류 없음)

- [ ] **T006** — `.env.example`에 배포용 환경변수 키 문서화
  - 구현 파일: `.env.example`(수정)
  - 상세: `MYSQL_JDBC_URL`, `MONGODB_URI`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` 등 신규 키를 값 없이(플레이스홀더 주석과 함께) 추가
  - 완료 기준: 실제 자격증명 값이 커밋되지 않는다

### Phase 2. 사용자 수동 작업 `[수동]`

> 아래 태스크는 각 서비스의 계정 소유자만 수행할 수 있어 AI가 대행하지 않는다. AI는 각 단계에서 필요한 정보(가입 URL, 설정 값 이름)를 안내한다.

- [ ] **T007** `[수동]` — MongoDB Atlas M0 클러스터 생성
  - 관련 요구사항: `FR-005`
  - 상세: 무료 M0 클러스터 생성 후 연결 문자열(`mongodb+srv://...`) 확보. Network Access에서 `0.0.0.0/0`(Render의 고정 IP가 아니므로 전체 허용 불가피 — 포트폴리오 목적상 허용) 등록

- [ ] **T008** `[수동]` — Upstash Redis 인스턴스 생성
  - 관련 요구사항: `FR-005`
  - 상세: 무료 데이터베이스 생성 후 host/port/password 확보(TLS 필수)

- [ ] **T009** `[수동]` — Aiven MySQL 서비스 생성
  - 관련 요구사항: `FR-005`
  - 상세: 무료 티어 서비스 생성 후 host/port/DB명/계정 확보. Flyway 마이그레이션(V1~V3)이 최초 기동 시 자동 적용됨을 확인

- [ ] **T010** `[수동]` (T003, T007, T008, T009 완료 후) — Render Web Service 생성 및 환경변수 등록
  - 관련 요구사항: `FR-003`, `FR-005`, `FR-006`
  - 상세: GitHub 저장소 연동, Environment를 Docker로 설정, `Dockerfile` 경로 지정, T007~T009에서 확보한 접속 정보를 Environment Variables로 등록(`ANTHROPIC_API_KEY` 포함), **Auto-Deploy는 반드시 Off**, Health Check Path를 `/actuator/health`로 설정
  - 완료 기준: Render 대시보드에서 최초 수동 배포가 성공하고 서비스 URL이 발급된다

- [ ] **T011** `[수동]` (T010 완료 후) — GitHub Actions Secret 등록
  - 관련 요구사항: `FR-006`
  - 상세: 저장소 Settings → Secrets and variables → Actions에 `RENDER_DEPLOY_HOOK_URL`(T010에서 발급된 Render Deploy Hook URL) 등록

- [ ] **T012** `[수동]` — main 브랜치 보호 규칙 설정
  - 관련 요구사항: `FR-002`
  - 상세: 저장소 Settings → Branches에서 main에 대해 "Require status checks to pass before merging" 활성화, `test` job을 필수 체크로 지정

### Phase 3. 검증 (SC-XXX 확인)

- [ ] **T013** `[P]` — SC-001 검증
  - 시나리오: 임의 브랜치에 사소한 변경을 push한 뒤 GitHub Actions 탭에서 `test` job이 자동 실행되는지 확인

- [ ] **T014** (T012 완료 후) — SC-002 검증
  - 시나리오: 의도적으로 실패하는 테스트를 담은 커밋으로 PR을 생성해 병합 버튼이 비활성화되는지 확인. 확인 후 해당 PR/브랜치는 정리(삭제)한다

- [ ] **T015** (T005, T010, T011 완료 후) — SC-003, SC-005 검증
  - 시나리오: main에 사소한 변경(예: README)을 병합한 뒤 15분 이내에 배포된 URL의 `/actuator/health`가 200을 반환하는지, 변경 사항이 반영되었는지 확인

- [ ] **T016** (T012, T015 완료 후) — SC-004 검증
  - 시나리오: 실패하는 테스트를 담은 커밋을 관리자 권한으로 강제 병합해 `deploy` job이 스킵되거나 실패로 종료되는지, 배포된 서비스가 이전 버전으로 유지되는지 확인. 확인 후 즉시 되돌린다(정상 커밋으로 재병합)

- [ ] **T017** `[P]` — SC-006 검증
  - 시나리오: `git grep`으로 저장소 전체에서 평문 비밀번호·API 키 패턴을 검색해 매치가 없음을 확인

## 구현 완료 기준

- [ ] 모든 태스크(코드 + 수동 작업 + 검증)가 완료 처리되었다.
- [ ] `./gradlew test`가 전체 PASSED를 반환한다(기존 001~004 테스트 회귀 없음).
- [ ] 배포된 공개 URL이 `/actuator/health`에서 200을 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다(실제 자격증명이 담긴 파일 포함 여부 재확인).
