# Diff: 006-infrastructure-as-code

## 커밋 메시지 한 줄 요약

- **KO**: 005에서 수동 프로비저닝한 MongoDB Atlas·Upstash·Aiven 관리형 DB 3종을 재생성 없이 Terraform import로 편입하고, 전체 리소스에 대해 `terraform plan`이 "No changes"를 반환함을 검증했다.
- **EN**: Bring the three managed databases (MongoDB Atlas, Upstash, Aiven) manually provisioned in 005 under Terraform management via `terraform import` without recreating them, verifying `terraform plan` reports "No changes" across all resources.

## 변경 요약

애플리케이션 코드는 전혀 변경하지 않고 저장소에 `infra/terraform/` 디렉토리를 신설했다. `mongodbatlas`/`upstash`/`aiven` 세 Terraform provider를 구성하고, 각 서비스의 관리 API(Atlas Admin API, Upstash Developer API, Aiven API)를 직접 호출해 조회한 실제 값을 기반으로 5개 리소스(Atlas 클러스터·DB 사용자·IP Access List, Upstash 데이터베이스, Aiven MySQL 서비스) 코드를 작성했다. `terraform import`로 기존 실제 리소스를 state에 편입한 뒤, 코드와 실제 상태의 diff를 하나씩 제거해 최종적으로 전체 `terraform plan`이 "No changes"를 반환하도록 만들었다 — 이 과정에서 `comment`(Atlas IP Access List), `tls`(Upstash)처럼 코드에 명시하지 않으면 기본값이 적용되어 리소스 재생성(destroy+create)을 유발하는 필드들을 실제로 발견하고 수정했다. Render(컴퓨트)는 공식 Terraform provider가 없어 범위에서 제외하고 계속 수동 관리한다. import 작업 전후로 배포된 애플리케이션의 헬스체크가 계속 200을 반환해 무중단을 확인했으며, 저장소 전체에 평문 자격증명이 없음을 확인했다.

## 변경 파일 및 라인 수

| 파일 | 추가 | 삭제 |
|---|---|---|
| `.gitignore` | +9 | -0 |
| `infra/terraform/providers.tf` | +30 | -0 |
| `infra/terraform/variables.tf` | +16 | -0 |
| `infra/terraform/atlas.tf` | +46 | -0 |
| `infra/terraform/upstash.tf` | +7 | -0 |
| `infra/terraform/aiven.tf` | +6 | -0 |
| `infra/terraform/terraform.tfvars.example` | +6 | -0 |
| `infra/terraform/.terraform.lock.hcl` | +66 | -0 |

**합계**: 8 files changed, 186 insertions(+), 0 deletions(-) (spec 문서 자체 변경분 제외. `terraform.tfstate`/`terraform.tfvars`는 gitignore 대상이라 이 저장소에 존재하지 않음)

## Diff

> 전체 diff는 문서에 박제하지 않는다 — git이 형상관리 SoT. base commit(005 완료 직후, 006 설계 착수 직전)과 재생성 명령만 기록한다.

- base commit: `90f6264` ([docs] 005-ci-cd-deploy 구현 완료 기록 및 context/infra 현행화)
- 재생성 명령: `git diff 90f6264 HEAD -- infra .gitignore`
