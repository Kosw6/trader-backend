# 629: 팀 재가입·삭제 운영 DB 변경과 배포 검증

2026-10-29 KST. 사용자 요청: 필요한 CPU 서버만 켜서 운영 DB 변경·배포·실제 재가입/팀 삭제를 검증하고 종료 후 모두 중지.

## 결과

V049를 운영 `application` 스키마에 적용하고, 검토한 클래스 5개만 기존 백엔드 JAR에 반영했다. 실제 공개 HTTPS API와 새 합성 계정으로 **19개 확인 항목이 모두 통과**했다. 기존 운영 가입 요청은 변경 전·마이그레이션 후·시험 자료 정리 후의 행 수와 요약 해시가 동일하다. 운영 원문 행은 복사하거나 반출하지 않았다.

기동 범위는 기존 DB·origin/backend·Redis·Kafka CPU 4대다. GPU·임베딩은 기동하지 않았다. origin 부팅 때 시작된 CPU AI API도 시험 전 중지했고 모델 평가·수집·전역 색인 작업은 수행하지 않았다.

## 배포 범위와 복구

- 이미지: `trader-backend:candidate629`, SHA `6e1074d9fdf5f24b592e62cba523e48410e50e4a9c58475423623c2a315cd460`.
- 변경 클래스: `TeamService`, `TeamRepository`, `JoinRequestRepository`, `JoinRequest`, `TeamController`.
- 기존 실제 운영 JAR과 최종 JAR의 전체 ZIP 항목을 대조해 이 5개만 변경됨을 확인했다. 작업 폴더의 다른 변경은 포함하지 않았다. 배포용 TeamRepository에서는 이번 수정과 무관한 로컬 `lockForChat` 추가도 제외했다.
- 실제 실행 컨테이너의 `/app/app.jar` SHA와 후보 JAR SHA 일치를 종료 전에 재확인했다.
- CPU 롤백 `trader-backend:rollback629`는 기존 운영 JAR에 JoinRequest의 구형 유일 제약 매핑 제거만 반영했다. DB 변경 후 실제 기동·health 200을 먼저 확인하고 새 후보로 교체했다. 새 후보도 health 200을 통과했다.
- 양쪽 모두 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`을 사용한다. 구형 제약을 자동으로 재생성하지 않는다. DB 이력 삭제를 동반하는 스키마 역변경은 복구 절차에 포함하지 않는다.
- 구 운영 컨테이너와 시험 롤백 컨테이너는 `restart=no`로 보존한다. 최종 운영 백엔드와 기존 CPU AI는 다음 호스트 기동 때 시작할 수 있도록 `restart=always`를 보존하고 이번 종료 전에 정상 중지했다.

기존 비공개 S3의 `deploy/team629-20261029/classes.tar.gz`에 클래스만 전달했다. API 키·사용자 원문·DB 행은 포함하지 않았다. 설정과 기존 JAR은 기존 서버의 비공개 릴리스 디렉터리에만 보존했다.

## DB 변경

백엔드 쓰기를 먼저 중지하고 `trader-controller/db/migration/V049__team_join_request_history.sql`을 적용했다. 3초 잠금 한도와 단일 트랜잭션 안에서 기존 `(team_id,requester_id)` 유일 제약을 제거하고 `status='PENDING'`에만 적용되는 부분 유일 인덱스를 만들었다. 기존 승인·거절 이력은 유지했다.

기존 운영 요청 1건의 집계 해시는 마이그레이션 전후와 시험 종료 후 모두 동일했다. 이는 특정 시점의 기존 가입 요청 집합 보존 검증이며, 전체 DB의 모든 테이블을 해시 대사한 결과는 아니다. 금융·주가 테이블에 대한 변경은 수행하지 않았다.

## 실제 API 확인 항목

| 범위 | 결과 |
|---|---|
| 최초 요청, 중복 PENDING 요청 | 요청 1건만 유지 |
| 최초 승인, 팀 조회, 탈퇴 | 정상 성공 |
| 동시 재가입 요청 2개 | 응답 둘 다 200, 새 PENDING ID 1개 |
| 과거 승인 요청 재처리 | 204 멱등 응답, 새 요청은 PENDING 유지, 회원 자격 복구 없음 |
| 비소유자 승인 | 거부 |
| 새 요청 승인, 재탈퇴 | 정상 성공 |
| 거절 후 재요청 | 별도 새 ID 발급 |
| 이력 보존 | 해당 합성 팀에 APPROVED 2 / REJECTED 1 / PENDING 1을 DB 집계로 확인 |
| 비소유자 팀 삭제 | 거부, 팀 유지 |
| 이력 4건을 가진 팀 삭제 | SQL 수동 정리 없이 API 204 |
| 다른 팀과 요청 | 정상 유지 |
| PENDING 요청을 가진 두 번째 팀 삭제 | SQL 수동 정리 없이 API 204 |

삭제 API의 추가 결함도 함께 수정했다. 기존 메서드가 URL 경로의 팀 ID를 명시적으로 바인딩하지 않아 query parameter에 의존했다. `@PathVariable`을 추가하고, `/api/team/A?teamId=B`의 충돌 상황에서도 A의 권한 검사와 삭제를 수행하는 회귀 테스트 및 실제 API 시험을 통과했다. B 팀과 그 요청이 보존됨도 확인했다.

마지막 로컬 회귀 실행은 42개 통과, 실패·오류·건너뜀 0이다. 직전 단계에서 동일 V049에 대한 실제 격리 PostgreSQL 이력/동시성/롤백 테스트 3개도 통과했다. 이번 운영 시험은 합성 계정 2개·팀 2개로 수행한 기능 확인이며, 20명 부하 시험이나 모든 팀 하위 콘텐츠 삭제 조합을 검사한 것은 아니다.

## 정리와 종료 증거

합성 팀 2개와 가입 요청은 시험 API로 삭제했고 계정 2개도 정상 삭제했다. 마지막 DB 읽기에서 해당 합성 users/team/join_request/user_team 잔여 0, 준비된 거래 0, 다른 열린 거래 0을 확인했다. 기존 가입 요청 집계도 원래 값과 일치했다.

배포 준비 중 ZIP 메타데이터 객체가 원본 비교에 재사용되어 무결성 검사가 실패한 시도가 있었다. 아직 활성화되지 않은 후보 생성만 중단됐고 DB·운영 JAR은 변경되지 않았다. 메타데이터를 복사해 생성한 후보와 롤백의 전체 항목 대사를 다시 통과한 뒤 배포했다. 실패 영수증은 보존했다.

자동 승인 검토가 가입 요청 원문 복사를 민감정보 반출로 분류하여 최초 사전 점검을 차단했다. 원문을 보관·반출하지 않고 PostgreSQL 내부 집계 해시만 사용하는 경로로 변경해 검증했다. 차단된 원문 복사 경로는 실행하지 않았다.

CPU 4대 정상 중지를 완료했다. 활성 17개 리전 전수 읽기에서 running/pending/stopping 0을 확인했다. EBS 7개·222GiB와 이미지·설정은 보존했다. 최종 서버 중지 정본은 저장소 상위 `output/team-release629-20261029/infra/stopped.json`, `all-regions-after.json`, `final-summary.json`이다. 서버가 꺼져 있는 동안 서비스가 가용하다고 보고하지 않는다.

## 영수증

저장소 상위 `output/team-release629-20261029/`:

- `scope629.json`, `artifact/manifest.json`: 승인 범위와 변경 클래스 SHA
- `preflight/`: 실제 운영 클래스와 집계 전용 DB 기준값
- `deployment/prepared.json`, `migration.json`, `started.json`
- `evaluation/checks.json`, `history-before-delete.json`, `complete.json`, `cleanup.json`
- `deployment/final-artifact.json`, `final-database.json`
- `tests/summary.json`, 테스트 XML
- `infra/`: 기동·중지·전 리전 검증
