# Spec: infrastructure-as-code

> Branch: 006-infrastructure-as-code | Date: 2026-07-13 | Version: v0.1.0

## 목차

- [배경 및 목적](#배경-및-목적)
- [사용자 스토리 (User Stories)](#사용자-스토리-user-stories)
- [기능 요구사항 (Functional Requirements)](#기능-요구사항-functional-requirements)
- [비기능 요구사항 (Non-Functional Requirements)](#비기능-요구사항-non-functional-requirements)
- [수용 기준 (Acceptance Criteria)](#수용-기준-acceptance-criteria)
- [범위 외 (Out of Scope)](#범위-외-out-of-scope)
- [미결 사항 (Open Questions)](#미결-사항-open-questions)

## 배경 및 목적

005에서 배포에 필요한 관리형 서비스(MongoDB Atlas, Upstash, Aiven MySQL, Render)를 각 서비스의 웹 대시보드에서 수동으로 프로비저닝했다. 이 방식은 설정 이력이 코드로 남지 않아, 리소스를 재현하거나 변경 이력을 추적하기 어렵고, 채용공고가 요구하는 "IaC(Infrastructure as Code)" 역량을 보여주지 못한다.

이번 spec은 005에서 이미 만든 관리형 데이터베이스 3종(MongoDB Atlas, Upstash Redis, Aiven MySQL)의 구성을 코드로 정의하고, 기존에 수동으로 만든 실제 리소스와 그 코드가 일치함을 검증하는 것을 목표로 한다. 리소스를 다시 만들지 않고 기존 리소스를 코드 관리 대상으로 편입시킨다(무중단, 접속 정보·URL 불변).

## 사용자 스토리 (User Stories)

- **US-001**: 개발자(나)로서, 데이터베이스 리소스의 설정(인스턴스 크기, 리전, 네트워크 접근 규칙 등)이 코드로 남아 있어 변경 이력을 git으로 추적하고 싶다.
- **US-002**: 개발자(나)로서, 노트북을 잃어버리거나 서비스를 재구축해야 할 때, 코드만으로 동일한 구성의 인프라를 다시 만들 수 있다는 확신을 갖고 싶다.
- **US-003**: 개발자(나)로서, 인프라 코드에 실제 비밀번호나 API 토큰이 그대로 노출되지 않기를 원한다.

## 기능 요구사항 (Functional Requirements)

- **FR-001**: MongoDB Atlas 클러스터의 구성(인스턴스 크기, 리전, 데이터베이스 사용자, 네트워크 접근 규칙)이 코드로 정의된다.
- **FR-002**: Upstash Redis 데이터베이스의 구성이 코드로 정의된다.
- **FR-003**: Aiven MySQL 서비스의 구성이 코드로 정의된다.
- **FR-004**: 코드로 정의된 구성을 005에서 이미 프로비저닝된 실제 리소스에 연결해, 코드와 실제 상태가 일치함을 확인할 수 있다.
- **FR-005**: 인프라 코드 저장소에는 실제 자격증명(API 토큰, 비밀번호)이 평문으로 포함되지 않는다.

## 비기능 요구사항 (Non-Functional Requirements)

- **NFR-001**: 인프라 코드 관리 도구 사용에 비용이 발생하지 않는다(무료).
- **NFR-002**: 코드와 실제 리소스를 연결하는 과정에서 기존 리소스가 재생성되지 않는다(다운타임 없음, 접속 정보·URL 불변).

## 수용 기준 (Acceptance Criteria)

- **SC-001** (`FR-001~003`): 3개 관리형 서비스 각각의 구성을 코드로 정의한 상태에서 초기화 명령이 오류 없이 완료된다.
- **SC-002** (`FR-004`, `NFR-002`): 기존에 프로비저닝된 3개 리소스를 코드 관리 상태로 편입시킨 뒤, 코드와 실제 리소스 상태를 비교하면 "변경 없음"으로 나타난다(리소스가 재생성되지 않았음을 의미).
- **SC-003** (`FR-005`): 인프라 코드 저장소(git history 포함)의 어떤 파일에도 실제 API 토큰·비밀번호가 평문으로 존재하지 않는다.
- **SC-004** (`NFR-002`): 편입 과정 전후로 배포된 애플리케이션의 헬스체크가 계속 정상(200)이다(무중단 확인).

## 범위 외 (Out of Scope)

- **Render(컴퓨트) IaC화** — Render는 공식 Terraform provider가 없고, 자체 IaC 형식(`render.yaml` Blueprint)은 기존 서비스를 편입하는 개념이 아니라 신규 생성 개념이라 005의 수동 설정과 맞지 않는다. Render는 계속 웹 UI로 관리한다.
- **CI에서의 인프라 코드 자동 실행(plan-on-PR 등)** — 이번 spec은 로컬에서 코드와 실제 상태를 일치시키는 것까지만 다룬다. CI 통합은 향후 spec 후보다.
- **신규 리소스 생성·리소스 삭제** — 기존 3개 리소스를 코드로 편입하는 것이 범위이며, 구성 변경이나 신규 리소스 추가는 다루지 않는다.
- **Terraform state의 원격 저장(Terraform Cloud, S3 등)** — 1인 포트폴리오 프로젝트 특성상 로컬 state로 충분하다고 판단한다.

## 미결 사항 (Open Questions)

없음. 기존 리소스 처리 방식(재생성이 아닌 기존 리소스 편입)과 Render 범위 제외는 사용자와의 대화로 확정하였다.
