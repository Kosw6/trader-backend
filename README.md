# Trader

주식 차트 분석 및 지식 그래프 기반 협업 플랫폼

---

## 프로젝트 소개

Trader는 주식 차트 조회와 노트 작성, 그래프 캔버스 기능을 결합한 주식 분석 플랫폼입니다.

- 종목별 차트를 보면서 날짜에 맞는 **매매 노트**를 작성하고 차트 위에 오버레이로 확인
- **그래프 캔버스**에서 노드·엣지·디렉토리·페이지 단위로 분석 아이디어를 시각적으로 구성
- **팀 캔버스**로 팀원과 분석 자료를 함께 작성·공유

---

## 주요 기능

### 주식 차트 & 노트
- 종목 검색 후 캔들차트(OHLCV) 조회
- 특정 날짜 기준 이전/이후 데이터 범위 조회
- 차트와 연동된 노트 오버레이 표시
- 종목별·날짜 범위별 노트 필터링 및 페이지네이션

### 개인 그래프 캔버스
- 디렉토리 → 페이지 → 노드·엣지 계층 구조
- 노드에 종목 심볼·날짜·내용 기록, 노트와 다대다 연결
- 엣지 스타일(타입·라벨·애니메이션·색상) 커스터마이징
- 노드 위치 별도 저장으로 드래그 최적화

### 팀 협업 캔버스
- 초대 코드(6자리) 기반 팀 가입 요청 → 승인/거절 워크플로우
- 팀 내 OWNER / MANAGER / MEMBER 권한 구조
- 팀 전용 디렉토리·페이지·노드·엣지·노트 공유
- 팀 이벤트(가입 승인·강퇴·역할 변경) 실시간 알림

### 인증
- 폼 기반 로그인 (loginId + password) + JWT 쿠키 인증
- OAuth2 소셜 로그인 (Google, Kakao, GitHub)
- Access Token 30분 / Refresh Token 7일, httpOnly 쿠키

---

## 기술 스택

### Backend
| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.3 |
| Security | Spring Security, JWT (Auth0), OAuth2 Client |
| ORM | Spring Data JPA / Hibernate |
| DB | PostgreSQL |
| Cache | Redis (Spring Cache) |
| WebSocket | Spring WebSocket|
| Monitoring | Micrometer + Prometheus |
| Docs | SpringDoc OpenAPI 2.8.3 |
| Build | Gradle |

### Frontend
| 분류 | 기술 |
|------|------|
| Framework | React 18 + Vite |
| UI | Material-UI (MUI) |
| Graph Canvas | @xyflow/react |
| Chart | lightweight-charts, Chart.js, react-financial-charts |
| WebSocket | @stomp/stompjs |
| HTTP | Axios |
| Styling | Tailwind CSS |

---

## 프로젝트 구조

```
trader-backend/
├── src/main/java/com/example/trader/
│   ├── controller/          # REST API 컨트롤러
│   ├── entity/              # JPA 엔티티 (User, Note, Team, Page, Node, Edge ...)
│   ├── service/             # 비즈니스 로직
│   ├── repository/          # Spring Data JPA 레포지토리
│   ├── dto/                 # 요청/응답 DTO
│   └── security/
│       ├── config/          # SecurityConfig
│       ├── filter/          # JwtFilter
│       ├── oauth2/          # CustomOAuth2UserService, OAuth2SuccessHandler
│       └── provider/        # JwtTokenProvider
└── src/main/resources/
    ├── application.yml
    └── application-prod.yml
```

---

## 엔티티 관계

```
User ─── UserTeam ─── Team
 │                      └── JoinRequest
 │
 ├── Note
 │
 └── Directory (계층, self-reference)
      └── Page
           ├── Node ─── NodeNoteLink ─── Note
           └── Edge
```

---

## API 엔드포인트 요약

| 도메인 | 메서드 | 경로 | 설명 |
|--------|--------|------|------|
| 인증 | POST | `/api/login/signin` | 로그인 |
| 인증 | POST | `/api/login/signup` | 회원가입 |
| 인증 | GET | `/api/login/refresh` | 토큰 갱신 |
| 인증 | POST | `/api/login/logout` | 로그아웃 |
| 유저 | GET | `/api/user/me` | 현재 유저 조회 |
| 노트 | GET | `/api/note` | 노트 목록 (페이지네이션) |
| 노트 | POST | `/api/note/save` | 노트 생성 |
| 노트 | GET | `/api/note/stock` | 종목별 노트 |
| 노트 | GET | `/api/note/range` | 날짜 범위 노트 |
| 페이지 | GET/POST | `/api/pages` | 페이지 목록/생성 |
| 노드 | GET/POST | `/api/pages/{pageId}/nodes` | 노드 목록/생성 |
| 엣지 | POST | `/api/pages/{pageId}/edges` | 엣지 생성 |
| 그래프 | GET | `/api/graph/{pageId}` | 페이지 전체 그래프 로드 |
| 디렉토리 | GET/POST | `/api/directories` | 디렉토리 목록/생성 |
| 팀 | GET | `/api/team/myTeams` | 내 팀 목록 |
| 팀 | POST | `/api/team` | 팀 생성 |
| 팀 가입 | POST | `/api/team/teams/join-requests` | 가입 요청 |
| 팀 가입 | PATCH | `/api/team/teams/join-requests/{id}` | 승인/거절 |
| 주식 | GET | `/api/stock` | 차트 데이터 조회 |
| 알림 | GET | `/api/notification/unRead` | 읽지 않은 알림 |

전체 API 명세: `http://localhost:8080/swagger-ui/index.html`

---

## 로컬 실행

### 사전 요구사항
- Java 17
- PostgreSQL
- Redis

### 환경변수

| 변수명 | 설명 |
|--------|------|
| `OAUTH_GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `OAUTH_GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 Secret |
| `OAUTH_KAKAO_CLIENT_ID` | Kakao OAuth 클라이언트 ID |
| `OAUTH_KAKAO_CLIENT_SECRET` | Kakao OAuth 클라이언트 Secret |
| `OAUTH_GITHUB_CLIENT_ID` | GitHub OAuth 클라이언트 ID |
| `OAUTH_GITHUB_CLIENT_SECRET` | GitHub OAuth 클라이언트 Secret |
| `APP_OAUTH2_REDIRECT_URL` | OAuth2 로그인 성공 후 리다이렉트 URL (기본값: `http://localhost:5173/login/success`) |
| `APP_COOKIE_SECURE` | HTTPS 배포 시 `true` 설정 (기본값: `false`) |

### 실행

```bash
./gradlew bootRun
```

기본 포트: `8080`
