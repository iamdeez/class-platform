# Research: ci-cd-deploy

## 목차

- [기존 코드베이스 분석](#기존-코드베이스-분석)
- [기술 선택 조사](#기술-선택-조사)
- [엣지 케이스 및 한계](#엣지-케이스-및-한계)

## 기존 코드베이스 분석

### 현재 설정 파일 구조

- `build.gradle.kts`: Spring Boot Gradle 플러그인(3.3.4)만 있고 `spring-boot-starter-actuator`가 없다 — SC-005(헬스체크 200 응답)를 만족할 표준 엔드포인트가 현재 존재하지 않는다.
- `src/main/resources/application.yml`: 아래 3개 저장소 연결 설정이 모두 **로컬 전용 형태로 하드코딩**되어 있어, 관리형 클라우드 서비스로 그대로 전환할 수 없다.

  | 설정 | 현재 값 | 문제 |
  |---|---|---|
  | `spring.datasource.url` | `jdbc:mysql://localhost:3306/${MYSQL_DATABASE:...}?useSSL=false&...` | host(`localhost:3306`)가 하드코딩. Aiven MySQL은 다른 host:port와 `sslMode=REQUIRED` 파라미터가 필요 |
  | `spring.data.mongodb.uri` | `mongodb://${MONGO_ROOT_USERNAME:root}:...@localhost:27017/...?authSource=admin` | MongoDB Atlas는 `mongodb+srv://`(포트 없음, DNS SRV 기반) 스킴을 쓰므로 현재 템플릿 구조 자체로는 표현 불가 |
  | `spring.data.redis.host`/`port` | `${REDIS_HOST:localhost}`/`${REDIS_PORT:6379}` | `password`/`ssl` 옵션이 아예 없음. Upstash Redis는 비밀번호 인증 + TLS가 필수 |

- `.dockerignore`: 존재하지 않는다. `Dockerfile`도 존재하지 않는다.
- `.github/`: 존재하지 않는다(CI 워크플로우 없음).
- `.env.example`: 로컬 개발용 변수만 정의되어 있고, 배포용 변수(관리형 서비스 접속 정보)는 없다.
- Git 원격 저장소: `https://github.com/iamdeez/class-platform.git` (이미 GitHub에 존재, private 여부는 미확인이나 GitHub Actions 사용에는 지장 없음).

### 영향 파일 목록

| 파일 | 변경 유형 | 영향 내용 |
|---|---|---|
| `build.gradle.kts` | 수정 | `spring-boot-starter-actuator` 추가(헬스체크 엔드포인트) |
| `src/main/resources/application.yml` | 수정 | `spring.datasource.url`/`spring.data.mongodb.uri`를 환경변수로 **전체 문자열 오버라이드 가능**하도록 변경(로컬 기본값은 유지, 하위 호환). `spring.data.redis.password`/`ssl.enabled` 추가. Actuator 헬스 엔드포인트 노출 설정 추가 |
| `Dockerfile` | 신규 | 멀티스테이지 빌드(Gradle 빌드 스테이지 → 최소 JRE 런타임 스테이지) |
| `.dockerignore` | 신규 | `~/.claude/rules/on-demand/docker.md` 표준 블록 기반 |
| `.github/workflows/ci.yml` | 신규 | push/PR 시 전체 테스트 스위트 실행(Testcontainers, GitHub Actions ubuntu-latest에 Docker 사전 설치되어 있어 별도 설정 불필요) |
| `.github/workflows/deploy.yml` | 신규 | main 브랜치 병합 + CI 통과 시에만 배포 트리거(Render Deploy Hook 호출) |
| `.env.example` | 수정 | 배포용 신규 환경변수(관리형 서비스 접속 정보) 항목 추가(값은 비워둔 채 키만 문서화) |

## 기술 선택 조사

### CI 플랫폼: GitHub Actions

저장소가 이미 GitHub에 있으므로 별도 CI 서비스 연동 없이 바로 사용 가능하다. Private 저장소 기준 Free 플랜에서 월 2,000분의 Linux 러너 무료 할당량을 제공하며(2026년 기준), 이 프로젝트 규모의 테스트 스위트(Testcontainers 포함, 전체 실행 시간 3~4분 수준)로는 여유 있게 충분하다. Public 저장소라면 무제한이다.

### 배포 스택: 관리형 서비스 조합 (사용자 선택)

3개 저장소(MySQL·MongoDB·Redis) + JVM 앱을 무료로 운영할 수 있는 대안을 조사한 결과(2026년 기준):

- **컴퓨트(앱)**: Render.com 무료 Web Service — Dockerfile 기반 배포 지원, 750 인스턴스-시간/월 무료. 단, 15분 유휴 시 슬립 → 다음 요청 시 콜드 스타트(약 30~60초)가 발생한다(NFR-002가 요구하는 "무료"는 만족하지만 "상시 즉시 응답"은 보장하지 않음 — SC-003의 "15분 이내 배포 완료"는 슬립과 무관하게 배포 자체의 반영 시간을 의미하며, 슬립 후 콜드 스타트는 이 SC의 범위가 아니다).
- **MySQL**: Aiven 무료 티어(1GB 저장공간/RAM/1 CPU, 서비스 유형당 1개 무료, 카드 등록 불요). PlanetScale은 2026년 4월 무료 티어를 종료해 후보에서 제외했다.
- **MongoDB**: MongoDB Atlas M0(512MB, 무료 영구 제공, 시간 제한 없음). 001부터 사용 중인 Spring Data MongoDB와 그대로 호환.
- **Redis**: Upstash Redis 무료 티어(256MB, 월 50만 커맨드, 무료 영구 제공). TCP 프로토콜을 지원해 `Lettuce`(Spring Data Redis 기본 클라이언트)로 별도 라이브러리 교체 없이 접속 가능.

대안으로 조사했던 Oracle Cloud Always Free VM(단일 서버에 docker-compose 그대로 배포)은 콜드 스타트가 없고 기존 로컬 구성을 거의 그대로 재사용할 수 있어 매력적이지만, 서버 자체의 OS 패치·방화벽·HTTPS 설정을 직접 관리해야 하고 리전에 따라 무료 인스턴스 생성이 용량 부족으로 실패하는 사례가 보고되어, 관리 부담이 적은 관리형 서비스 조합을 사용자가 최종 선택했다.

## 엣지 케이스 및 한계

- **Aiven MySQL의 TLS 검증 수준**: `sslMode=REQUIRED`(암호화는 하되 서버 인증서를 검증하지 않음)로 연결하는 것이 가장 단순하다. `VERIFY_CA`까지 적용하려면 Aiven이 제공하는 CA 인증서를 애플리케이션에 번들링해야 하는데, 포트폴리오 목적상 과도한 복잡도로 판단해 `REQUIRED`로 단순화한다(실제 프로덕션이라면 `VERIFY_CA` 이상을 권장).
- **Render 무료 티어 콜드 스타트**: 슬립 상태에서의 첫 요청은 지연이 크다(30~60초). SC-005(헬스체크 200)를 자동화된 CI 검증으로 두기보다 배포 직후 수동 확인으로 처리하는 것이 실용적이다(무료 티어 특성상 상시 헬스체크 자동화는 아래 "테스트 전략"에서 판단).
- **GitHub Actions 병렬 실행과 Testcontainers 포트 충돌**: 이 저장소는 이미 Testcontainers 기반 통합 테스트가 다수 존재하며, 각 테스트가 독립적인 컨테이너 인스턴스를 사용하므로(`@Container` 클래스별로 별도 컨테이너) 단일 워크플로우 잡 내 순차 실행에서는 포트 충돌 우려가 없다. 병렬 매트릭스 실행은 이번 spec에서 도입하지 않는다(NFR-001 무료 할당량 절약 측면에서도 순차 실행이 유리).
- **민감 정보 유출 방지(FR-006/SC-006)**: GitHub Actions는 저장소 Settings의 "Secrets and variables" 관리 기능을 제공하며, 워크플로우 로그에 시크릿 값이 자동 마스킹된다. Render도 별도 Environment Variables 설정 화면을 제공해 값이 저장소 코드에 노출되지 않는다. 이 값들의 실제 등록(계정 생성, 서비스 프로비저닝, 값 입력)은 AI가 대행할 수 없는 사용자 수동 작업이다.
- **`spring.datasource.url`/`spring.data.mongodb.uri` 전체 오버라이드 방식의 하위 호환**: `${MYSQL_JDBC_URL:jdbc:mysql://localhost:...}` 형태로 바꾸면 환경변수 미설정 시 기존 로컬 개발 동작이 그대로 유지된다 — 004까지의 모든 테스트가 이 값에 의존하지 않으므로(Testcontainers는 `@DynamicPropertySource`로 `spring.datasource.url` 자체를 직접 오버라이드) 회귀 위험이 없다.
