# 417 — 그룹 캔버스 폴더·페이지 실시간 동기화

2026-10-20. 사용자가 그룹 폴더·페이지의 WebSocket/멀티서버 동기화와 검증 후 AWS 반영을 승인했다. Docker Hub 대상 `kosw006/trader-backend:20261020-tree417` 업로드도 별도로 명시 승인받았다.

## 변경

- 기존 그래프 연결은 선택한 graphId에만 묶여 있어 폴더·페이지 변경이 전파되지 않았다. 그래프 커서/락 경로를 변경하지 않고, 그룹 단위 읽기 전용 `/ws/team-tree?teamId=…` 연결을 추가했다. 그래프를 선택하지 않아도 그룹 트리를 구독한다.
- 기존 JWT 쿠키 인증 및 설정된 CORS origin 제한을 적용한다. 구독 시 멤버십을 검사하고 이벤트 전달/25초 heartbeat 때도 재확인한다. 인증 없음/타 그룹 접근/클라이언트의 변경 이벤트 위조를 차단한다. 데이터 수정은 기존 권한 검사 REST API로만 한다.
- 팀 폴더·페이지 생성/이름 변경/이동/삭제/폴더 하위 트리 삭제에서 같은 DB 트랜잭션의 Outbox에 `RELIABLE / TEAM_TREE_CHANGED`를 저장한다. `teamId`만 스코프이며 `graphId`는 null이다. 이름/본문은 메시지에 싣지 않는다. 롤백 시 Outbox도 롤백되고 알림은 AFTER_COMMIT에만 전달된다.
- 기존 single/multi 커밋 전파, Redis Pub/Sub(`canvas:{teamId}:tree`), Kafka(`team:{teamId}:tree` key), 재시도/복구, 서버별 eventId 중복 억제 경로를 사용한다. Outbox relay는 이 신규 팀 이벤트에 한해서만 null graphId를 허용한다. 기존 노드 이벤트의 graphId 검증은 유지한다.
- 프론트는 트리 변경/구독 완료 신호를 받으면 권한 검사된 `/content/init`을 다시 읽는다. 80ms 병합, 한 번에 한 조회, 조회 중 변경 시 낡은 결과 폐기 후 재조회, 15초 요청 제한, 일시 실패 시 3초 간격 재시도, 팀 전환/언마운트 취소, 재접속·온라인 복귀·탭 복귀 복구를 넣었다. 401/403에서는 목록/그래프를 비우고 자동 조회 재시도를 멈춘다.
- 로컬 임시 트리 스냅샷으로 새 원격 결과를 덮어쓰지 않도록 팀 CRUD는 서버 확인 목록으로 갱신한다. 기존 폴더 펼침 상태를 보존한다. 선택한 페이지 이동 시 새 부모를 따라가고, 삭제 시 페이지/폴더 URL과 그래프를 정리한다.
- 삭제/다른 페이지 선택 후 열린 노드·연결선 모달의 입력은 복사용으로 보존하되 다른 그래프에 저장하지 못하도록 스코프를 검사한다. 대상이 사라진 노드의 자동 임시 저장도 멈춘다.
- 팀 페이지 이동 coordinator가 개인 `move(teamId, pageId, targetDirId)`를 잘못 호출하던 문제를 팀 전용 `teamPageMove`로 수정했다. 팀 하위 폴더 생성의 parentId 누락 및 자기 하위 폴더로 이동하는 순환도 방지했다.
- 로컬 Vite의 `/ws` 프록시도 추가했다.

## 검증

- 프론트 단위/계약 테스트 **247/247** 통과, Vite production build 성공. 기존 대형 chunk 경고는 남아 있다.
- 백엔드 관련 회귀 **138/138**, 실패/오류/skip 0. 실시간 패키지, Outbox, Redis 수신, 새 그룹 트리, 기존 디렉터리/페이지/가입 요청 서비스 포함. 외부 설정 제외 bootJar 성공.
- H2 트랜잭션 테스트에서 커밋 전 미전파, 롤백 후 Outbox/알림 없음, 커밋 후 팀 스코프 이벤트를 확인했다.
- 두 독립 서버 객체를 사용한 Redis listener/Kafka consumer 경로에서 서버별 중복 억제, 타 그룹 격리, Redis 미수신 이벤트의 Kafka 복구를 확인했다. 실제 Redis/Kafka 프로세스를 여러 대 새로 띄운 장애 실험은 아니다.
- 운영 DB를 **읽기 전용**으로 확인해 기존 Outbox `graph_id` nullable, `sub_type` 문자열 및 제약 호환성을 확인했다. 스키마 변경 없음.
- 운영에서 기존 승인 QA 계정의 임시 그룹만 만들고 실제 두 WebSocket 연결로 CRUD/이동/하위 삭제/READY/heartbeat/재접속/익명·잘못된 그룹 차단 **79개 검사 통과**. QA 그룹과 생성한 폴더·페이지는 모두 정리했다. 기존 사용자 그룹·노트·멤버십은 변경하지 않았다.
- 그 실행에서 생성한 이벤트 **12/12**가 운영 Outbox `SENT`, 실패 0, team-only 스코프임을 별도 읽기로 확인했다. Kafka 발행 ACK까지 확인한 것이며 실운영 백엔드 2대의 장애 전환을 확인한 것은 아니다.
- 공개 경로/기존 QA 로그인/노트 읽기/NVDA 일·주·월·년 OHLCV/보호 경로/AI READY **18/18** 회귀 통과. 브라우저 직접 조작 및 서로 다른 실제 계정 2명의 UI E2E는 수행하지 않았다.

## AWS 반영

- Backend: `kosw006/trader-backend:20261020-tree417`
- Digest: `sha256:620cff51132f8dc49a200e4012df1d49f3a7b5f939d6164a889a6368c8c7a227`
- JAR SHA256: `2ef907126ee5585645473e76b296d000364ad7c5c4cd2ad3b5efb1c81e9bf6d7`
- 이미지에 application 설정 파일 0개. 기존 origin backend만 교체, 환경/볼륨/네트워크/제한 보존 및 readiness 확인. 이전 `trader-backend-before-tree417` 컨테이너는 중지 상태로 보존했다.
- Frontend: S3 `trader-front-s3/web/releases/20261020-tree417`, CloudFront `E2Z3K2FSXCU8V4`. 정적 origin path만 변경, 이전 join416 release 보존. 파일 6개 해시 및 SPA 경로 10개 **VERIFIED**.
- 실제 실행 증거: 작업 루트 `output/deploy/20261020-tree417/`의 image/activation/frontend/tree-smoke/Outbox/public-smoke 영수증.
- GPU/AI/indexer/DB/Redis/Kafka 컨테이너를 재시작하거나 새 인프라를 만들지 않았다. 로컬 백필의 프로세스/계획/STOP/원문/실패 기록/요청 간격/DB는 변경하지 않았다. 최종 읽기에서 백필 상태 RUNNING, 배치 00818, 갱신 15:22:40 KST였다.

## 배포 호환성·한계

신규 enum 이벤트가 기존 Kafka/Outbox에 남으므로 416 이하 consumer와 혼합하는 rolling 배포를 안전하다고 간주하면 안 된다. 멀티 backend 확대 시 전 노드가 이 이벤트를 해석하는 버전인지 먼저 확인한다. 현재는 기존 origin 한 대만 교체했다. 프론트만 이전 정적 release로 돌리는 것과 backend consumer를 과거 버전으로 되돌리는 것은 다르며, 후자는 새 이벤트 호환성을 별도로 검토해야 한다. 이 작업에서 과거 이벤트를 삭제하거나 토픽을 초기화하지 않았다.

현재 트리 동기화는 이벤트의 개별 변경 내용을 재생하는 것이 아니라 서버의 최신 목록을 재조회하는 방식이다. 서버/연결 중단 동안의 중간 편집 과정은 재현하지 않고 재접속 시 최종 상태로 수렴한다.
