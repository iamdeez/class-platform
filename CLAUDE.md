# class-platform

## Constitution

이 프로젝트의 모든 작업은 `.claude/docs/constitution.md`의 조항을 최우선으로 준수한다.
spec, plan, tasks 작성 및 구현 전 반드시 constitution을 읽는다.

## Context

프로젝트 구조·이벤트 흐름·도메인 용어·알려진 제약은 `.claude/docs/context.md`에 정의한다.
새 spec 설계 전 반드시 읽어 전체 시스템 맥락을 숙지한다.
spec 구현·검증 완료 후 변경된 내용을 반영하여 갱신한다.

작성 규칙 및 템플릿은 `~/.claude/agents/00-context-init.md`를 참조한다.

## Infra

인프라 토폴로지·환경변수·배포 방식·운영 제약은 `.claude/docs/infra.md`에 정의한다.
배포·환경 구성에 영향을 주는 spec 설계 전 반드시 읽어 운영 제약을 파악한다.
인프라 변경 후 변경된 내용을 반영하여 갱신한다.

작성 규칙 및 템플릿은 `~/.claude/agents/00-context-init.md`를 참조한다.

---

## 프로젝트 성격

월부닷컴 Sr. Software Engineer(BE) 채용공고 대비 포트폴리오 연습 프로젝트다. 온라인 클래스 플랫폼(강의/커뮤니티) 미니 클론을 Kotlin + Spring Boot 기반으로 구현한다. 실제 서비스 운영 목적이 아닌 학습·포트폴리오 목적이다.

## 개발 진행 방식

사용자가 이 기술 스택(Kotlin/Spring Boot/JPA/MyBatis 등)에 아직 익숙하지 않아 학습하며 구현을 진행한다.

- 구현은 `tasks.md`의 태스크를 한 번에 몰아서 진행하지 않고, 작은 단위(태스크 하나 또는 그보다 작은 단위)로 나눠 매 단위마다 설명 → 사용자 검토·확인 → 다음 단위 진행 순서를 따른다.
- 커밋은 사용자가 직접 수행하며, 구현 단위도 "커밋 하나에 담기 좋은 크기"로 나눠 제안한다.
- 사용자가 명시적으로 "한 번에 다 진행해줘"라고 요청하면 이 방식을 완화한다.

## 스펙 문서 구조

`docs/specs/v{X}.{Y}.{Z}/{NNN-spec-name}/` 하위에 `spec.md`(WHAT) / `research.md`(사전 조사) / `plan.md`(HOW) / `tasks.md`(실행 단위)를 둔다. 상세 규칙은 `~/.claude/rules/specs/01-design-rules.md` 참조.

---

## AI 작업 폴더 (`_ai-workspace`)

AI가 개발 과정에서 정보 소통, 분석 메모, 중간 결과물 등을 위해 파일을 생성해야 할 경우,
해당 spec 폴더 내부의 **`_ai-workspace`** 폴더를 사용한다.

- 폴더명은 **`_ai-workspace`** 로 고정한다 (underscore prefix로 일반 spec 문서와 구분).
- AI는 이 폴더 내에서 파일을 **자유롭게 생성/수정/삭제**할 수 있다.
- 이 폴더의 파일은 정식 spec 문서가 아니며, 개발 완료 후 정리(삭제)해도 무방하다.
- 커밋 대상에서 제외하거나 포함하는 것은 사용자 판단에 따른다.
