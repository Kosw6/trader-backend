# 테스트 구분
이건 도메인 규칙인가? → Domain Test @Test

이건 분기/흐름인가? → Service(Mock)

이건 DB 매핑 신뢰성인가? → @DataJpaTest

이건 유저가 보는 흐름인가? → Integration, @SpringbootTest


----------------------
# (참고)계층별 역할
Controller는 HTTP 요청을 검증하고 인증 정보를 추출한 뒤,
유스케이스를 표현하는 서비스에 위임하고 DTO로 응답한다.

**Domain(Entity)**는 비즈니스 규칙과 상태 변경을 책임지며,
연관관계와 무결성을 스스로 보장한다.

Service는 도메인을 직접 구현하지 않고,
도메인 메서드를 호출하여 흐름·분기·트랜잭션을 조율한다.

Repository는 도메인을 DB에 저장하고 조회하는 역할만 수행한다.