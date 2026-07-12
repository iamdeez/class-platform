# Plan: infrastructure-as-code

> Branch: 006-infrastructure-as-code | Date: 2026-07-13 | Spec: [spec.md](./spec.md)

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

- [x] P-001 도메인 순수성: 이 spec은 애플리케이션 코드(`src/`)를 전혀 변경하지 않는다(순수 인프라 코드 추가). 위반 없음.
- [x] P-002 성능 원칙: 애플리케이션 실행 경로와 무관하다.
- [x] P-003 호환성 원칙: 기존 API 계약을 변경하지 않는다.
- [x] P-004 테스트 원칙: FR-001~005 모두 SC-001~004에 대응한다(FR-001~003→SC-001, FR-004→SC-002, FR-005→SC-003, NFR-002→SC-002/SC-004).
- [x] P-005 스펙 범위 원칙: Render IaC화·CI 통합·신규 리소스 생성은 spec.md에서 명시적으로 범위 외로 뺐다.

**예외 사항**: 없음.

## 기술 컨텍스트

- **IaC 도구**: Terraform(로컬 CLI, 원격 state 미사용)
- **Provider**: `mongodb/mongodbatlas`, `upstash/upstash`, `aiven/aiven`
- **대상 리소스**: MongoDB Atlas M0 클러스터+DB 사용자+IP Access List, Upstash Redis 데이터베이스, Aiven MySQL 서비스 — 3개 서비스 모두 005에서 이미 프로비저닝된 리소스를 `terraform import`로 편입

## 사전 영향도 분석 결과

이 spec은 애플리케이션 코드를 변경하지 않으며, 저장소에 새 디렉토리(`infra/terraform/`)만 추가한다.

| 파일 | 변경 유형 | 영향 내용 |
|---|---|---|
| `infra/terraform/providers.tf` | 신규 | Terraform/provider 버전 고정 + provider 설정 |
| `infra/terraform/variables.tf` | 신규 | 리소스 식별자·자격증명 변수 선언(자격증명은 `sensitive = true`) |
| `infra/terraform/atlas.tf` | 신규 | `mongodbatlas_advanced_cluster`, `mongodbatlas_database_user`, `mongodbatlas_project_ip_access_list` |
| `infra/terraform/upstash.tf` | 신규 | `upstash_redis_database` |
| `infra/terraform/aiven.tf` | 신규 | `aiven_mysql` |
| `infra/terraform/terraform.tfvars.example` | 신규 | 값 없는 플레이스홀더(커밋 대상) |
| `.gitignore` | 수정 | `infra/terraform/.terraform/`, `*.tfstate*`, `infra/terraform/terraform.tfvars` 추가 |
| `.claude/docs/infra.md` | 수정 | IaC 도입 반영(spec 완료 후) |

## 핵심 설계

### 디렉토리 구조

```
infra/terraform/
├── providers.tf
├── variables.tf
├── atlas.tf
├── upstash.tf
├── aiven.tf
└── terraform.tfvars.example
```

애플리케이션 소스(`src/`)와 명확히 분리해 인프라 코드가 별도 관심사임을 드러낸다.

### 자격증명 처리

Aiven/Atlas provider는 각각 `AIVEN_TOKEN`, `MONGODB_ATLAS_PUBLIC_API_KEY`/`MONGODB_ATLAS_PRIVATE_API_KEY`(또는 `MONGODB_ATLAS_CLIENT_ID`/`_SECRET`) 환경변수를 provider 블록 설정 없이 직접 읽으므로, 해당 두 provider는 **빈 provider 블록**으로 선언하고 실행 시 셸 환경변수로만 자격증명을 주입한다(코드에 값이 전혀 나타나지 않음). Upstash provider는 환경변수 지원 여부가 공식 문서로 확인되지 않아, `variable "upstash_email"`/`variable "upstash_api_key"`(둘 다 `sensitive = true`)로 선언하고 `terraform.tfvars`(gitignore 대상, 커밋 안 함)로 값을 주입한다 — 구현 착수 시 Upstash provider가 환경변수를 지원함이 확인되면 Aiven/Atlas와 동일한 방식으로 통일한다.

```hcl
# providers.tf (발췌)
terraform {
  required_providers {
    mongodbatlas = { source = "mongodb/mongodbatlas" }
    upstash      = { source = "upstash/upstash" }
    aiven        = { source = "aiven/aiven" }
  }
}

provider "mongodbatlas" {}  # MONGODB_ATLAS_PUBLIC_API_KEY / MONGODB_ATLAS_PRIVATE_API_KEY 환경변수로 인증
provider "aiven" {}         # AIVEN_TOKEN 환경변수로 인증
provider "upstash" {
  email   = var.upstash_email
  api_key = var.upstash_api_key
}
```

### Import 절차 (공통 패턴)

각 리소스마다: ① 코드에 리소스 블록을 먼저 선언(속성값은 실제 콘솔 값과 일치하도록 작성) → ② `terraform import`로 state에 편입 → ③ `terraform plan`으로 diff가 없는지 확인 → diff가 있으면 코드 쪽 속성을 실제 상태에 맞게 수정(리소스를 바꾸는 게 아니라 코드를 실제에 맞춘다).

```bash
# 예시 (Aiven)
terraform import aiven_mysql.class_platform_mysql "{aiven_project}/{service_name}"
terraform plan   # "No changes." 가 나올 때까지 aiven.tf 속성을 조정
```

`mongodbatlas_database_user`의 `password` 필드는 API가 실제 값을 반환하지 않아 상시 diff를 유발하므로, `lifecycle { ignore_changes = [password] }`를 처음부터 포함해 작성한다(research.md 참조).

## 인터페이스 계약

이 spec은 REST API나 애플리케이션 인터페이스를 추가·변경하지 않는다. "인터페이스"에 해당하는 것은 각 Terraform 리소스 블록의 속성 스키마이며, 이는 각 provider의 공식 문서를 따른다(research.md 참조).

## 데이터 모델

해당 없음 — 애플리케이션 도메인 데이터 모델 변경 없음.

## 테스트 전략

Terraform은 실제 클라우드 리소스를 대상으로 하므로 SC 검증은 대부분 CLI 명령의 결과 확인(운영 절차)이다 — 005와 동일한 성격(plan.md "기타 고려사항" 참조).

| SC 식별자 | 검증 방식 | 시나리오 요약 | 입력 | 기대 결과 |
|---|---|---|---|---|
| SC-001 | 수동(`terraform init`) | 3개 provider 선언 후 초기화 | `terraform init` | 오류 없이 provider plugin 다운로드 완료 |
| SC-002 | 수동(`terraform import` + `terraform plan`) | 3개 기존 리소스를 순서대로 import | `terraform import ...` ×3 | 각 리소스 import 성공, 이후 `terraform plan`이 "No changes." 반환 |
| SC-003 | 자동(`git grep`) | 저장소 전체에서 평문 API 토큰·비밀번호 패턴 검색 | `git grep` | 매치 없음(변수 참조·플레이스홀더만 존재) |
| SC-004 | 수동(`curl`) | import 전후로 배포 URL 헬스체크 | `curl {URL}/actuator/health` | import 전후 모두 `200 UP`(무중단 확인) |

## 기타 고려사항

- **AI가 대행할 수 없는 사전 준비**: Atlas Organization API Key, Upstash API Key, Aiven API Token 발급과 Project ID/서비스명/DB ID 확인은 각 서비스 계정 소유자만 할 수 있는 웹 UI 작업이다. 005와 동일하게 tasks.md에서 `[수동]`으로 구분한다.
- **Upstash provider 인증 방식의 불확실성**: research.md에서 밝힌 대로 구현 착수 시 재확인이 필요하다. 확인 결과에 따라 위 "자격증명 처리" 설계를 조정할 수 있다.
- **import 실패 시 원복 전략**: `terraform import`는 state에만 기록하는 명령이라 실패해도 실제 리소스에는 영향이 없다. 다만 실수로 `terraform apply`를 잘못 실행해 리소스가 변경/삭제되는 것을 막기 위해, import 및 plan 확인이 끝나기 전까지는 `apply`를 실행하지 않는다(NFR-002).
