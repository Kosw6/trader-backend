# 그룹 요구사항 기획서
## 요구사항
1. 그룹 CRUD
    - 그룹을 생성시에 랜덤 코드 6자리 발급 및 저장
    - 해당 코드로 다른 유저가 생성된 그룹에 합류 요청을 보낼 수 있음
    - 그룹의 역할로 OWNER, MEMBER
    - 그룹의 OWNER가 받은 요청들을 승인하게 될 경우 그룹에 합류
    - 이후 공유 캔버스를 제작가능
2. 알람 이벤트
    - 메인 페이지 상단에 알람 이모지 추가 및 숫자로 알람 수 표시
    - 알람 클릭시 받은 알람 리스트 출력
    - 알름은 실시간으로 뜨게 제작 예정(클라이언트 polling사용)
      - WebSocket대신 polling 사용 이유 : 매우 저지연의 실시간성이 요구되지 않으며 합류 요청이 드문 기능이므로 클라이언트의 polling요청을 대략 10~20초로 잡아 서버의 부하를 낮추려고 함 

## 도메인 _모델_

- User (1:*) UserTeam (*:1) Team
- Team
  - id, TeamName, code, userTeam, createdAt/updatedAt (BaseTimeEntity)
  - team_code UNIQUE, team_id UNIQUE
- UserTeam
  - id, User, Team, role(ENUM:OWNER,MEMBER), status(ENUM:PENDING,ACCEPT), createdAt/updatedAt
    - status : 팀 소유주에게 보낸 합류 요청 상태(ENUM:PENDING,ACCEPT)
    - owner : 팀당 소유주만 설정-한명
    - 차후 오너의 요청 승인 목록 조회에서 해당 팀의 PENDING상태의 요청+유저 간략 정보만 표시
    - (team_id, user_id) UNIQUE


- User (1:*) Notifycation
- Notifycation
  - id, userId(알람수신자),type: TEAM_JOIN_REQUEST, TEAM_JOIN_ACCEPTED, payload: (teamId, requesterUserId 등) JSON, isRead (boolean),createdAt
  - 상단 뱃지(안읽은 개수만 10~20초마다), 유저가 알람 아이콘을 눌렀을 때 마다 리스트를 호출

