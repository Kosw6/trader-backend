# Trader Backend

Trader Backend는 주식 차트 기반 복기 서비스의 백엔드입니다. 사용자는 종목 차트를 보며 매매 노트를 작성하고, 개인 또는 팀 단위의 그래프 캔버스에서 노드, 엣지, 페이지, 디렉터리로 투자 아이디어를 정리할 수 있습니다.

이 저장소는 단순 CRUD 백엔드에서 출발해 실시간 협업, Redis/Kafka 장애 대응, 관측 체계, 복구 검증까지 확장한 Spring Boot 기반 프로젝트입니다.

## 핵심 경험

- 주식 차트, 노트, 그래프 캔버스, 팀 협업 API 구현
- JWT/OAuth2 기반 인증과 팀 권한 검증
- WebSocket 기반 실시간 커서/드래그/락 이벤트 처리
- Redis Pub/Sub hot path와 Kafka/Outbox durable path 분리
- Redis 장애 시 lock/autosave/version hint의 DB fallback 검증
- TraceId/MDC/AOP 기반 JSON structured logging 구성
- Prometheus/Grafana/Loki 관측을 위한 actuator, Micrometer, logback 설정
- Grafana Alert, Lambda, SSM/ASG 기반 자동 복구와 scale-out 검증의 애플리케이션 대상

## Tech Stack

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.3 |
| API | Spring Web, SpringDoc OpenAPI |
| Security | Spring Security, JWT, OAuth2 Client |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Cache / State | Redis, Spring Cache |
| Realtime | Spring WebSocket, Redis Pub/Sub, Kafka, gRPC |
| Observability | Actuator, Micrometer, Prometheus, JSON Logback |
| Discovery | Eureka Client |
| Test | JUnit 5, Mockito, Testcontainers, H2 |
| Build / Runtime | Gradle, Docker |

## Domain Features

### Chart & Notes

- 종목 검색과 캔들 차트 데이터 조회
- 특정 종목/날짜 범위 기반 매매 노트 조회
- 차트와 연결되는 노트 작성, 수정, 삭제
- 종목별 복기 자료 관리

### Graph Canvas

- 디렉터리, 페이지, 노드, 엣지 기반 그래프 캔버스
- 노드에 종목, 날짜, 내용, 노트 연결 정보 저장
- 페이지 단위 그래프 초기 로딩 API
- 노드 위치 변경, 노드/엣지 수정, 삭제
- 개인 캔버스와 팀 캔버스 API 분리

### Team Collaboration

- 팀 생성, 수정, 삭제
- 초대/가입 요청, 승인/거절
- OWNER, MANAGER, MEMBER 역할 관리
- 팀별 페이지, 디렉터리, 그래프 리소스 접근 제어
- 팀 이벤트 알림

### Authentication

- loginId/password 기반 로그인
- JWT access token / refresh token cookie 발급
- Google, Kakao OAuth2 로그인
- `@AuthenticationPrincipal` 기반 현재 사용자 주입
- `@TeamMemberRequired` 기반 팀 멤버 권한 검증

## Realtime Architecture

실시간 협업 이벤트는 성격에 따라 두 경로로 나뉩니다.

```text
WebSocket
  -> CanvasRawWsHandler
  -> RealtimeEnvelope
      -> VOLATILE: cursor / drag preview
      -> RELIABLE: lock / edit control event
```

### Volatile Path

커서, 드래그 프리뷰처럼 유실 가능하지만 낮은 지연이 중요한 이벤트입니다.

```text
VOLATILE
  -> Redis Pub/Sub
  -> gRPC relay
  -> HTTP relay
  -> dropped
```

`realtime.volatile.route-mode`로 `redis`, `grpc`, `http`, `local` 경로를 선택할 수 있습니다.

### Reliable Path

락, 편집 시작/종료처럼 유실되면 안 되는 이벤트입니다.

```text
RELIABLE
  -> Redis Pub/Sub for realtime fanout
  -> Kafka for durable event log
  -> Outbox fallback when Kafka is unavailable
  -> Republish scheduler after recovery
```

Redis Pub/Sub은 실시간 fanout을 담당하고, Kafka는 복구와 catch-up을 위한 durable log 역할을 담당합니다. Kafka publish 실패 시 `RealtimeOutboxService`가 DB outbox에 저장하고, `RealtimeOutboxRepublishScheduler`가 Kafka 회복 후 재발행합니다.

## Degraded Mode

장애 상황에서 전체 기능이 5xx로 무너지지 않도록 Redis/Kafka 의존 경로를 분리했습니다.

| 장애 | 대응 |
|---|---|
| Redis Pub/Sub 장애 | gRPC/HTTP relay fallback 또는 volatile drop |
| Redis lock 장애 | `CanvasLockService`가 PostgreSQL 기반 DB lock fallback 사용 |
| Kafka 장애 | reliable event를 DB outbox에 저장 후 recovery 시 재발행 |
| Redis cache 장애 | DB 조회 경로로 기능 지속 |

이 구조의 목표는 모든 이벤트를 항상 보존하는 것이 아니라, 이벤트 성격에 따라 유실 가능 경로와 보존 필요 경로를 나누는 것입니다.

## Observability

요청 흐름과 장애 원인을 추적하기 위해 구조화 로그와 메트릭을 구성했습니다.

```text
HTTP Request
  -> TraceIdFilter
  -> MDC
  -> @ObservedLog AOP
  -> JSON Console Log
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

## Recovery / Operations

멀티 인스턴스 환경을 고려해 Eureka, internal health, recovery lifecycle을 구성했습니다.

- Eureka Client 등록과 metadata `public-host`, `status` 관리
- `/internal/health` 기반 gateway failover 확인
- `RecoveryOrchestrator`를 통한 broadcast/catch-up consumer 전환
- drain 시작 시 Eureka instance status를 `OUT_OF_SERVICE`로 변경
- Kafka catch-up 완료 후 broadcast mode 복귀

인프라 레벨에서는 별도 Terraform/AWS 환경에서 다음 흐름을 검증했습니다.

```text
Grafana Alert
  -> Lambda
  -> SSM RunCommand / ASG API
  -> docker compose restart app 또는 scale-out
  -> Prometheus scrape UP / Gateway 합류 확인
```

## Main API Groups

| 영역 | 경로 |
|---|---|
| Auth | `/api/login/signin`, `/api/login/signup`, `/api/login/refresh`, `/api/login/logout` |
| User | `/api/user/me`, `/api/user/{id}` |
| Note | `/api/note`, `/api/note/stock`, `/api/note/range` |
| Stock | `/api/stock`, `/api/suggest` |
| Personal Graph | `/api/directories`, `/api/pages`, `/api/pages/{pageId}/nodes`, `/api/pages/{pageId}/edges`, `/api/graph/{pageId}` |
| Team | `/api/team`, `/api/team/myTeams`, `/api/team/teams/join-requests` |
| Team Graph | `/api/teams/{teamId}/directories`, `/api/teams/{teamId}/pages`, `/api/teams/{teamId}/graphs/{graphId}` |
| Realtime Internal | `/internal/realtime/events`, `/internal/health` |
| WebSocket | `/ws/canvas-raw?teamId={teamId}&graphId={graphId}` |

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## Project Structure

```text
src/main/java/com/example/trader/
  controller/             REST API
  controller/teamGraph/   team-scoped graph API
  service/                domain service
  repository/             Spring Data JPA repository
  entity/                 JPA entity
  dto/                    request/response DTO
  security/               JWT, OAuth2, security filter/provider
  ws/                     WebSocket handler/session/lock
  realtime/               realtime envelope, publisher, outbox, recovery path
  infra/                  Redis/Kafka/gRPC/HTTP infrastructure adapters
  ops/recovery/           recovery lifecycle and catch-up/broadcast control
  observability/          TraceId, MDC, structured logging AOP
  health/                 health test utilities
```

## Local Run

### Requirements

- Java 17
- PostgreSQL
- Redis
- Kafka and Eureka are optional, depending on realtime mode

### Run

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

Default port:

```text
8080
```

### Docker Build

```bash
docker build -t trader-backend .
```

The Dockerfile has two runtime targets:

- `prod`: JRE image for normal runtime
- `profile`: JDK image for profiling/JFR/debugging

## Useful Configuration

| 설정 | 설명 | 기본값 |
|---|---|---|
| `APP_MODE` | single/multi mode | `multi` in default config |
| `REALTIME_VOLATILE_ROUTE_MODE` | volatile event route | `redis` |
| `REALTIME_RELIABLE_ROUTE_MODE` | reliable event route | `redis-only` |
| `REALTIME_KAFKA_ENABLED` | Kafka reliable path enable | `false` |
| `REALTIME_REDIS_PUBSUB_ENABLED` | Redis Pub/Sub enable | `true` |
| `REALTIME_GRPC_ENABLED` | gRPC relay enable | `false` |
| `REALTIME_HTTP_ENABLED` | HTTP relay enable | `false` |
| `REALTIME_OUTBOX_RETRY_ENABLED` | outbox retry enable | `true` |
| `EDIT_SESSION_FALLBACK_MODE` | edit session fallback mode | `db` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka server URL | `http://localhost:8761/eureka/` |

OAuth2 관련 값:

- `OAUTH_GOOGLE_CLIENT_ID`
- `OAUTH_GOOGLE_CLIENT_SECRET`
- `OAUTH_KAKAO_CLIENT_ID`
- `OAUTH_KAKAO_CLIENT_SECRET`
- `APP_OAUTH2_REDIRECT_URL`
- `APP_COOKIE_SECURE`

운영 환경에서는 DB password, OAuth secret, JWT 관련 값은 환경변수나 secret manager로 분리해야 합니다.

## Tests

```bash
./gradlew test
```

테스트 범위:

- domain tests: User, Team, UserTeam
- service tests: Directory, Edge, Graph, Note, Page, Team, User
- canvas tests: lock fallback, edit session, conflict validation
- persistence tests: PostgreSQL Testcontainers 기반 repository 검증
- Redis race condition/fallback 관련 테스트

## Notes

- 이 프로젝트의 realtime 설계에서 Redis Pub/Sub은 hot path, Kafka는 durable replay/catch-up path입니다.
- `VOLATILE` 이벤트는 유실 가능성을 허용하고 낮은 지연을 우선합니다.
- `RELIABLE` 이벤트는 Kafka/Outbox를 통해 복구 가능한 경로를 갖습니다.
- 관측과 장애 검증은 별도 `engineering-notes`와 load test scenario에서 함께 관리했습니다.
