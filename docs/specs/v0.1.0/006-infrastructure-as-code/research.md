# Research: infrastructure-as-code

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

### 현재 인프라 구성 (005 완료 상태)

`infra.md`에 문서화된 대로, 005에서 다음 3개 관리형 DB가 웹 UI로 수동 프로비저닝되었으며 이번 spec의 편입(import) 대상이다:

| 서비스 | 리소스 | 식별 정보 |
|---|---|---|
| MongoDB Atlas | M0 무료 클러스터 + 데이터베이스 사용자 + Network Access(`0.0.0.0/0`) | 클러스터명·Project ID는 Atlas 콘솔에서 확인 필요(현재 문서에 미기록) |
| Upstash | Redis 무료 데이터베이스 | Endpoint `pure-tortoise-98812.upstash.io`(호스트명에서 DB 식별 가능하나 Terraform import용 ID는 별도 확인 필요) |
| Aiven | MySQL 무료 서비스 | 서비스명은 호스트명(`class-platform-mysql-deezcreator-b418...`)에서 유추 가능하나 정확한 Aiven 프로젝트명·서비스명은 콘솔에서 재확인 필요 |

이 정보들은 006 구현 착수 시 각 콘솔에서 직접 확인해야 한다(env.txt에는 접속 자격증명만 있고, Terraform 관리용 프로젝트/서비스 식별자는 없음).

### 새로 필요한 자격증명(control-plane API 토큰)

005의 `env.txt`에 있던 정보는 애플리케이션이 DB에 **접속**하기 위한 자격증명(username/password)이며, Terraform이 리소스를 **관리**하려면 각 서비스의 관리 API 토큰이 별도로 필요하다 — 아직 발급되지 않았다.

| 서비스 | 필요한 자격증명 | 발급 위치(콘솔) |
|---|---|---|
| MongoDB Atlas | Organization API Key(Public/Private key) 또는 Service Account(Client ID/Secret) | Organization Settings → Access Manager → API Keys |
| Upstash | API Key + 계정 이메일 | Upstash Console → Account → API |
| Aiven | API Token | Aiven Console → 계정 메뉴 → Personal Tokens |

## 기술 선택 조사

### Terraform Provider 조사 결과 (2026년 기준, Terraform Registry/공식 문서 근거)

#### MongoDB Atlas (`mongodb/mongodbatlas`)

- **인증**: 권장 방식은 Service Account(`client_id`/`client_secret`, 환경변수 `MONGODB_ATLAS_CLIENT_ID`/`MONGODB_ATLAS_CLIENT_SECRET`). 레거시 방식(Programmatic API Key, `public_key`/`private_key`, 환경변수 `MONGODB_ATLAS_PUBLIC_API_KEY`/`MONGODB_ATLAS_PRIVATE_API_KEY`)도 여전히 동작한다.
- **리소스**: `mongodbatlas_advanced_cluster`를 사용한다 — 이전 `mongodbatlas_cluster`는 공식 마이그레이션 가이드가 있는 사실상 대체 대상이다.
- **M0 무료 클러스터 설정**: `replication_specs[].region_configs[].electable_specs.instance_size = "M0"`, `provider_name = "TENANT"`, `backing_provider_name`을 실제 클라우드(AWS 등)로 지정.
- **Import ID 형식**(공식 문서 확인):
  - 클러스터: `{PROJECT_ID}-{CLUSTER_NAME}`
  - 데이터베이스 사용자: `{PROJECT_ID}/{USERNAME}/{AUTH_DATABASE}` (슬래시 형식 — 하이픈 형식은 사용자명에 하이픈이 있으면 깨짐)
  - IP Access List: `{PROJECT_ID}-{CIDR}` (예: `...-0.0.0.0/0`)
- **주의**: `database_user` import 후 `password`를 설정 파일에 그대로 두면 매번 "변경 있음"으로 나온다(Atlas API가 실제 비밀번호를 반환하지 않기 때문). `password` 필드를 생략하거나 `lifecycle { ignore_changes = [password] }`를 적용해야 SC-002(변경 없음)를 만족한다.

#### Upstash (`upstash/upstash`)

- **인증**: `email` + `api_key` provider 필드. **환경변수명은 공식 문서에서 명확히 확인되지 않음** — 구현 시 `registry.terraform.io/providers/upstash/upstash/latest/docs`를 직접 재확인해야 한다(추측 금지).
- **리소스**: `upstash_redis_database`.
- **Import**: 빈 리소스 블록(`resource "upstash_redis_database" "redis" {}`)을 먼저 선언한 뒤 `terraform import upstash_redis_database.redis {db-id}`. `{db-id}`를 콘솔 어디서 확인하는지도 구현 시 재확인 필요(Upstash REST API로 조회 가능할 것으로 추정).

#### Aiven (`aiven/aiven`)

- **인증**: `api_token` provider 필드, 환경변수 `AIVEN_TOKEN`(확인됨).
- **리소스**: `aiven_mysql`.
- **Import**(확인됨): `terraform import aiven_mysql.NAME {PROJECT}/{SERVICE_NAME}`.

### Terraform state 저장 방식: 로컬 state

1인 포트폴리오 프로젝트이고 CI 통합은 이번 spec의 범위 밖(spec.md 범위 외 참조)이므로, 원격 state(Terraform Cloud, S3 등)를 도입하지 않고 로컬 `terraform.tfstate` 파일을 사용한다. `terraform.tfstate`는 리소스의 상세 속성(민감 정보 포함 가능)을 담으므로 `.gitignore`에 반드시 추가한다.

## 엣지 케이스 및 한계

- **Upstash provider 문서 불확실성**: 조사 결과 Upstash Terraform provider의 인증 환경변수명과 `{db-id}` 확인 방법이 공식 문서에서 명확히 확인되지 않았다. 구현 착수 시 `terraform providers schema` 또는 provider 소스코드로 직접 재확인이 필요하다(추측으로 HCL을 작성하지 않는다).
- **Atlas M0 클러스터의 API 제약 가능성**: 웹 검색 중 "M0 클러스터는 API로 생성할 수 없다"는 언급이 있었다(직접 공식 문서로 재확인은 못함). 이는 `apply`로 신규 생성하는 경우의 제약으로 추정되며, 이미 콘솔로 생성된 M0 클러스터를 `import`하는 이번 spec의 시나리오에는 영향이 없을 것으로 판단하되, 실제 `terraform plan`이 예상과 다르게 나오면 재검토한다.
- **Database user password 처리**(위 Atlas 절 참조): import 직후 `password` 필드로 인한 상시 diff 문제를 `lifecycle.ignore_changes`로 회피해야 SC-002를 만족한다. 이 패턴은 Upstash/Aiven의 비밀번호성 필드에도 유사하게 적용될 수 있어 각 리소스 import 후 `terraform plan` 결과를 개별 확인해야 한다.
- **정확한 리소스 식별자 확보가 선행 조건**: Project ID(Atlas)·서비스명(Aiven)·DB ID(Upstash)는 코드 작성 전에 각 콘솔에서 사용자가 직접 확인해야 하는 값이다(AI가 로그인해서 조회할 수 없음) — tasks.md에서 별도 태스크로 명시한다.
