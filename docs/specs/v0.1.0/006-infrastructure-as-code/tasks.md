# Tasks: infrastructure-as-code

> Branch: 006-infrastructure-as-code | Date: 2026-07-13 | Plan: [plan.md](./plan.md)

## 목차

- [전제 조건](#전제-조건)
- [태스크 목록](#태스크-목록)
- [구현 완료 기준](#구현-완료-기준)

## 전제 조건

- [x] spec.md의 모든 `[NEEDS CLARIFICATION]` 항목이 해소되었는가? — 미결 사항 없음.
- [x] plan.md의 Constitution Gates가 모두 통과(또는 예외 기재)되었는가? — 5개 조항 모두 통과, 예외 없음.
- [x] CHANGES.md에서 이전 작업의 "후속 작업 시 주의사항"을 확인했는가? — 005 CHANGES.md의 "IaC 미도입" 항목이 이번 spec의 출발점이다.

## 태스크 목록

> `[P]` 표시: 이전 태스크와 병렬 실행 가능. `[수동]` 표시: AI가 대행할 수 없는 사용자 작업(웹 UI 계정·설정 조작).

### Phase 1. 사전 준비 `[수동]`

> 각 서비스의 관리 API 자격증명과 리소스 식별자를 확보한다. AI는 발급 위치와 필요한 값을 안내한다.

- [x] **T001** `[수동]` `[P]` — MongoDB Atlas API Key 발급 + 식별자 확인
  - 관련 요구사항: `FR-001`, `FR-004`
  - 상세: Organization Settings → Access Manager → API Keys에서 발급(Project 읽기/쓰기 권한). Project ID, 클러스터명, 데이터베이스 사용자명 확인
  - 완료 기준: API Key(Public/Private 또는 Client ID/Secret) 및 3개 식별자 확보 — 확인 완료 (사용자가 직접 수행. 레거시 Public/Private API Key 발급, Project ID·클러스터명 확인)
  - **구현 중 발견한 이슈**: 현재 Atlas UI에는 plan.md가 가정한 "Access Manager" 메뉴가 사이드바에 없었다(IDENTITY & ACCESS 하위 Applications/Users/Teams/Federation 구조로 변경됨). 발급된 키의 형식(8자리 영숫자 Public Key + UUID 형태 Private Key)으로 보아 레거시 Programmatic API Key 방식으로 발급됨 — Service Account(Client ID/Secret) 방식이 아니므로 providers.tf 작성 시 `MONGODB_ATLAS_PUBLIC_API_KEY`/`MONGODB_ATLAS_PRIVATE_API_KEY` 환경변수 경로를 사용해야 한다

- [x] **T002** `[수동]` `[P]` — Upstash API Key 발급 + DB 식별자 확인
  - 관련 요구사항: `FR-002`, `FR-004`
  - 상세: Upstash Console → Account → API에서 발급. 대상 Redis 데이터베이스의 ID 확인
  - 완료 기준: API Key + 계정 이메일 + DB ID 확보 — 확인 완료 (사용자가 API Key 발급, AI가 Upstash Developer API(`GET /v2/redis/databases`)로 조회해 `database_id: 763bfc83-3314-45ce-8966-f09839b5c0ba` 확인. endpoint·password가 기존 005 접속 정보와 일치함을 확인해 올바른 DB임을 검증)
  - **구현 중 발견한 이슈**: Upstash Terraform provider는 환경변수를 자동으로 읽지 않는다(Upstash CLI와 달리). `email`/`api_key`를 `terraform.tfvars`로 명시 전달해야 한다(research.md 불확실성 해소 — plan.md "자격증명 처리" 절에 이미 이 경로를 대비해뒀음). 콘솔에서 DB ID가 표시되는 위치는 확인하지 못했고, API 직접 조회로 대체함

- [x] **T003** `[수동]` `[P]` — Aiven API Token 발급 + 서비스 식별자 확인
  - 관련 요구사항: `FR-003`, `FR-004`
  - 상세: Aiven Console → 계정 메뉴 → Personal Tokens에서 발급. 프로젝트명, 서비스명 확인
  - 완료 기준: API Token + 프로젝트명·서비스명 확보 — 확인 완료 (사용자가 Personal Token 발급, AI가 Aiven API(`GET /v1/project`, `GET /v1/project/{project}/service`)로 조회해 프로젝트명 `deezcreator-b418`, 서비스명 `class-platform-mysql` 확인. host가 기존 접속 정보와 일치함을 검증)

### Phase 2. Terraform 코드 작성 (T001~T003 완료 후)

- [ ] **T004** — Terraform 프로젝트 초기 구조 + provider 설정
  - 구현 파일: `infra/terraform/providers.tf`(신규), `.gitignore`(수정)
  - 관련 요구사항: `FR-001~003`
  - 상세: `required_providers`(mongodbatlas/upstash/aiven), 빈 provider 블록(Atlas/Aiven은 환경변수 인증), `.gitignore`에 `infra/terraform/.terraform/`, `*.tfstate*`, `infra/terraform/terraform.tfvars` 추가
  - 완료 기준: `terraform init`이 오류 없이 완료된다(SC-001)

- [ ] **T005** `[P]` — Atlas 리소스 코드 작성
  - 구현 파일: `infra/terraform/atlas.tf`(신규), `infra/terraform/variables.tf`(신규 또는 확장)
  - 관련 요구사항: `FR-001`
  - 상세: `mongodbatlas_advanced_cluster`(M0), `mongodbatlas_database_user`(`lifecycle.ignore_changes = [password]` 포함), `mongodbatlas_project_ip_access_list`(`0.0.0.0/0`)
  - 완료 기준: `terraform validate` 통과

- [ ] **T006** `[P]` — Upstash 리소스 코드 작성
  - 구현 파일: `infra/terraform/upstash.tf`(신규), `infra/terraform/variables.tf`(확장), `infra/terraform/terraform.tfvars.example`(신규)
  - 관련 요구사항: `FR-002`
  - 상세: `upstash_redis_database` 빈 블록으로 시작. provider 인증 방식은 구현 시 공식 문서(`registry.terraform.io/providers/upstash/upstash/latest/docs`)로 재확인 후 확정(research.md 참조)
  - 완료 기준: `terraform validate` 통과

- [ ] **T007** `[P]` — Aiven 리소스 코드 작성
  - 구현 파일: `infra/terraform/aiven.tf`(신규)
  - 관련 요구사항: `FR-003`
  - 상세: `aiven_mysql` 빈 블록으로 시작
  - 완료 기준: `terraform validate` 통과

### Phase 3. Import 및 검증 (T004~T007, T001~T003 완료 후)

- [ ] **T008** — Atlas 리소스 3종 import
  - 관련 요구사항: `FR-004`, `NFR-002`
  - 상세: `terraform import mongodbatlas_advanced_cluster.NAME {project_id}-{cluster_name}` 등 3개 리소스 순서대로 import 후 `terraform plan`으로 diff 확인, diff가 있으면 `atlas.tf` 속성을 실제 값에 맞게 수정
  - 완료 기준: `terraform plan`이 해당 3개 리소스에 대해 "No changes"를 반환한다

- [ ] **T009** `[P]` — Upstash 리소스 import
  - 관련 요구사항: `FR-004`, `NFR-002`
  - 상세: `terraform import upstash_redis_database.redis {db-id}` 후 `terraform plan`으로 diff 확인·조정
  - 완료 기준: `terraform plan`이 "No changes"를 반환한다

- [ ] **T010** `[P]` — Aiven 리소스 import
  - 관련 요구사항: `FR-004`, `NFR-002`
  - 상세: `terraform import aiven_mysql.class_platform_mysql {project}/{service_name}` 후 `terraform plan`으로 diff 확인·조정
  - 완료 기준: `terraform plan`이 "No changes"를 반환한다

- [ ] **T011** (T008~T010 완료 후) — SC-002, SC-004 통합 검증
  - 시나리오: 전체 `terraform plan`이 3개 리소스 모두 "No changes"임을 최종 확인(SC-002). import 작업 전후로 배포된 URL(`https://class-platform-quan.onrender.com/actuator/health`)이 계속 200을 반환하는지 확인(SC-004, 무중단)

- [ ] **T012** `[P]` — SC-003 검증
  - 시나리오: `git grep`으로 `infra/terraform/` 및 저장소 전체에서 평문 API 토큰·비밀번호 패턴을 검색해 매치가 없음을 확인(변수 참조·플레이스홀더만 존재해야 함)

## 구현 완료 기준

- [ ] 모든 태스크(수동 작업 + 코드 + import + 검증)가 완료 처리되었다.
- [ ] `terraform plan`이 3개 리소스 모두 "No changes"를 반환한다.
- [ ] `git status`에 의도치 않은 파일이 없다(`terraform.tfstate`, `terraform.tfvars`, `.terraform/`이 커밋되지 않았는지 재확인).
- [ ] import 과정 중 배포된 애플리케이션의 헬스체크가 계속 200이었다(무중단 확인).
