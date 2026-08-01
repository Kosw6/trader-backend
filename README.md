# Trader Backend

Trader Backend는 주식 차트 기반 복기 서비스의 백엔드입니다. 사용자는 종목 차트를 보며 매매 노트를 작성하고, 개인 또는 팀 단위의 그래프 캔버스에서 노드, 엣지, 페이지, 디렉터리로 투자 아이디어를 정리할 수 있습니다.

이 저장소는 단순 CRUD 백엔드에서 출발해 실시간 협업, Redis/Kafka 장애 대응, 관측 체계, 복구 검증까지 확장한 Spring Boot 기반 프로젝트입니다.

## 핵심 경험

- 주식 차트, 노트, 그래프 캔버스, 팀 협업 API 구현
- JWT/OAuth2 기반 인증과 팀 권한 검증
- WebSocket 기반 실시간 커서/드래그/락 이벤트 처리
- Redis Pub/Sub 저지연 경로와 Kafka/Outbox 복구 가능 경로 분리
- Redis 장애 시 lock, autosave, version hint의 DB 대체 경로 검증
- TraceId, MDC, AOP 기반 JSON 구조화 로그 구성
- Prometheus/Grafana/Loki 관측을 위한 actuator, Micrometer, logback 설정
- Grafana Alert, Lambda, SSM/ASG 기반 자동 복구와 확장 검증 대상

## 기술 스택

| 분류 | 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.4.3 |
| API | Spring Web, SpringDoc OpenAPI |
| 인증 및 보안 | Spring Security, JWT, OAuth2 Client |
| 데이터 접근 | Spring Data JPA, Hibernate, PostgreSQL |
| 캐시 및 상태 | Redis, Spring Cache |
| 실시간 처리 | Spring WebSocket, Redis Pub/Sub, Kafka, gRPC |
| 관측 | Actuator, Micrometer, Prometheus, JSON Logback |
| 서비스 검색 | Eureka Client |
| 테스트 | JUnit 5, Mockito, Testcontainers, H2 |
| 빌드 및 실행 | Gradle, Docker |

## 주요 기능

### 차트와 노트

- 종목 검색과 캔들 차트 데이터 조회
- 특정 종목/날짜 범위 기반 매매 노트 조회
- 차트와 연결되는 노트 작성, 수정, 삭제
- 종목별 복기 자료 관리

### 그래프 캔버스

- 디렉터리, 페이지, 노드, 엣지 기반 그래프 캔버스
- 노드에 종목, 날짜, 내용, 노트 연결 정보 저장
- 페이지 단위 그래프 초기 로딩 API
- 노드 위치 변경, 노드/엣지 수정, 삭제
- 개인 캔버스와 팀 캔버스 API 분리

### 팀 협업

- 팀 생성, 수정, 삭제
- 초대/가입 요청, 승인/거절
- OWNER, MANAGER, MEMBER 역할 관리
- 팀별 페이지, 디렉터리, 그래프 리소스 접근 제어
- 팀 이벤트 알림

### 인증

- loginId와 password 기반 로그인
- JWT access token과 refresh token cookie 발급
- Google, Kakao OAuth2 로그인
- `@AuthenticationPrincipal` 기반 현재 사용자 주입
- `@TeamMemberRequired` 기반 팀 멤버 권한 검증

## 실시간 처리 구조

실시간 협업 이벤트는 성격에 따라 두 경로로 나뉩니다.

```text
WebSocket
  -> CanvasRawWsHandler
  -> RealtimeEnvelope
      -> VOLATILE: cursor / drag preview
      -> RELIABLE: lock / edit control event
```

### 휘발성 이벤트 경로

커서, 드래그 프리뷰처럼 유실 가능하지만 낮은 지연이 중요한 이벤트입니다.

```text
VOLATILE
  -> Redis Pub/Sub
  -> gRPC relay
  -> HTTP relay
  -> dropped
```

`realtime.volatile.route-mode`로 `redis`, `grpc`, `http`, `local` 경로를 선택할 수 있습니다.

### 복구 가능 이벤트 경로

락, 편집 시작/종료처럼 유실되면 안 되는 이벤트입니다.

```text
RELIABLE
  -> Redis Pub/Sub 실시간 전파
  -> Kafka 복구용 이벤트 로그
  -> Kafka 장애 시 DB Outbox 저장
  -> 복구 후 재발행 Scheduler
```

Redis Pub/Sub은 실시간 전파를 담당하고, Kafka는 복구와 누락 이벤트 재처리를 위한 이벤트 로그 역할을 담당합니다. Kafka 발행 실패 시 `RealtimeOutboxService`가 DB Outbox에 저장하고, `RealtimeOutboxRepublishScheduler`가 Kafka 복구 후 재발행합니다.

## 장애 시 기능 유지

장애 상황에서 전체 기능이 5xx로 무너지지 않도록 Redis/Kafka 의존 경로를 분리했습니다.

| 장애 | 대응 |
|---|---|
| Redis Pub/Sub 장애 | gRPC/HTTP 대체 전파 또는 휘발성 이벤트 폐기 |
| Redis lock 장애 | `CanvasLockService`가 PostgreSQL 기반 DB lock 사용 |
| Kafka 장애 | 복구 필요 이벤트를 DB Outbox에 저장한 뒤 복구 시 재발행 |
| Redis cache 장애 | DB 조회 경로로 기능 지속 |

이 구조의 목표는 모든 이벤트를 항상 보존하는 것이 아니라, 이벤트 성격에 따라 유실 가능 경로와 보존 필요 경로를 나누는 것입니다.

## 관측

요청 흐름과 장애 원인을 추적하기 위해 구조화 로그와 메트릭을 구성했습니다.

```text
HTTP 요청
  -> TraceIdFilter
  -> MDC
  -> @ObservedLog AOP
  -> JSON Console 로그
  -> Promtail / Loki / Grafana
```

주요 로그 필드:

- `traceId`
- `method`
- `uri`
- `clientIp`
- `service`
- `instance`
- `host`
- `domain`
- `api`
- `event`
- `elapsedMs`
- `errorCode`
- `severity`
- `httpStatus`

Actuator와 Micrometer를 통해 다음 지표를 Prometheus로 노출합니다.

- `http.server.requests`
- `hikaricp.connections.*`
- `jvm.*`
- `realtime.volatile.relay.*`
- `realtime.kafka.publish.*`
- `realtime.outbox.save.*`
- `realtime.relay.*`

## 복구와 운영

멀티 인스턴스 환경을 고려해 Eureka, 내부 상태 확인 API, 복구 생명주기를 구성했습니다.

- Eureka Client 등록과 metadata `public-host`, `status` 관리
- `/internal/health`를 사용한 Gateway 장애 전환 확인
- `RecoveryOrchestrator`를 통한 실시간 전파와 누락 이벤트 재처리 Consumer 전환
- 요청 배출 시작 시 Eureka instance status를 `OUT_OF_SERVICE`로 변경
- Kafka 누락 이벤트 처리를 마친 뒤 실시간 전파 모드로 복귀

인프라 레벨에서는 별도 Terraform/AWS 환경에서 다음 흐름을 검증했습니다.

```text
Grafana Alert
  -> Lambda
  -> SSM RunCommand / ASG API
  -> docker compose 애플리케이션 재시작 또는 확장
  -> Prometheus 수집 상태와 Gateway 재합류 확인
```

## 주요 API

| 영역 | 경로 |
|---|---|
| 인증 | `/api/login/signin`, `/api/login/signup`, `/api/login/refresh`, `/api/login/logout` |
| 사용자 | `/api/user/me`, `/api/user/{id}` |
| 노트 | `/api/note`, `/api/note/stock`, `/api/note/range` |
| 주가 | `/api/stock`, `/api/suggest` |
| 개인 그래프 | `/api/directories`, `/api/pages`, `/api/pages/{pageId}/nodes`, `/api/pages/{pageId}/edges`, `/api/graph/{pageId}` |
| 팀 | `/api/team`, `/api/team/myTeams`, `/api/team/teams/join-requests` |
| 팀 그래프 | `/api/teams/{teamId}/directories`, `/api/teams/{teamId}/pages`, `/api/teams/{teamId}/graphs/{graphId}` |
| 내부 실시간 처리 | `/internal/realtime/events`, `/internal/health` |
| WebSocket | `/ws/canvas-raw?teamId={teamId}&graphId={graphId}` |

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## 프로젝트 구조

```text
src/main/java/com/example/trader/
  controller/             REST API
  controller/teamGraph/   팀 단위 그래프 API
  service/                도메인 서비스
  repository/             Spring Data JPA Repository
  entity/                 JPA Entity
  dto/                    요청 및 응답 DTO
  security/               JWT, OAuth2, 보안 Filter와 Provider
  ws/                     WebSocket Handler, Session, Lock
  realtime/               실시간 이벤트, Publisher, Outbox, 복구 경로
  infra/                  Redis, Kafka, gRPC, HTTP Adapter
  ops/recovery/           복구 생명주기와 재처리/전파 제어
  observability/          TraceId, MDC, 구조화 로그 AOP
  health/                 상태 확인용 도구
```

## 로컬 실행

### 필요 환경

- Java 17
- PostgreSQL
- Redis
- Kafka와 Eureka는 실시간 처리 모드에 따라 선택적으로 사용

### 실행

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

기본 포트:

```text
8080
```

### Docker 이미지 빌드

```bash
docker build -t trader-backend .
```

Dockerfile은 두 가지 실행 target을 제공합니다.

- `prod`: 일반 실행을 위한 JRE 이미지
- `profile`: Profiling, JFR, Debugging을 위한 JDK 이미지

## 주요 설정

| 설정 | 설명 | 기본값 |
|---|---|---|
| `APP_MODE` | 단일/다중 인스턴스 모드 | 기본 설정 `multi` |
| `REALTIME_VOLATILE_ROUTE_MODE` | 휘발성 이벤트 전달 경로 | `redis` |
| `REALTIME_RELIABLE_ROUTE_MODE` | 복구 필요 이벤트 전달 경로 | `redis-only` |
| `REALTIME_KAFKA_ENABLED` | Kafka 복구 경로 사용 여부 | `false` |
| `REALTIME_REDIS_PUBSUB_ENABLED` | Redis Pub/Sub 사용 여부 | `true` |
| `REALTIME_GRPC_ENABLED` | gRPC 전파 사용 여부 | `false` |
| `REALTIME_HTTP_ENABLED` | HTTP 전파 사용 여부 | `false` |
| `REALTIME_OUTBOX_RETRY_ENABLED` | Outbox 재시도 사용 여부 | `true` |
| `EDIT_SESSION_FALLBACK_MODE` | 편집 Session 대체 경로 | `db` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka Server URL | `http://localhost:8761/eureka/` |

OAuth2 관련 값:

- `OAUTH_GOOGLE_CLIENT_ID`
- `OAUTH_GOOGLE_CLIENT_SECRET`
- `OAUTH_KAKAO_CLIENT_ID`
- `OAUTH_KAKAO_CLIENT_SECRET`
- `APP_OAUTH2_REDIRECT_URL`
- `APP_COOKIE_SECURE`

운영 환경에서는 DB password, OAuth secret, JWT 관련 값은 환경변수나 secret manager로 분리해야 합니다.

## 테스트

```bash
./gradlew test
```

테스트 범위:

- 도메인 테스트: User, Team, UserTeam
- 서비스 테스트: Directory, Edge, Graph, Note, Page, Team, User
- 캔버스 테스트: Lock 대체 경로, 편집 Session, 충돌 검증
- 영속성 테스트: PostgreSQL Testcontainers 기반 Repository 검증
- Redis 경합 상태와 대체 경로 관련 테스트

## 참고

- 이 프로젝트의 실시간 처리에서 Redis Pub/Sub은 저지연 경로, Kafka는 복구와 누락 이벤트 재처리 경로입니다.
- `VOLATILE` 이벤트는 유실 가능성을 허용하고 낮은 지연을 우선합니다.
- `RELIABLE` 이벤트는 Kafka/Outbox를 통해 복구 가능한 경로를 갖습니다.
- 관측과 장애 검증은 별도 `engineering-notes`와 부하 테스트 Scenario에서 함께 관리했습니다.
