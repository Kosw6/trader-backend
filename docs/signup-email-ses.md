# SES 기반 일반 회원가입 이메일 인증

현재 상태(2026-11-01 후속): 백엔드·프론트 구현, SES DKIM/IAM 설정, 사용자 직접 실제 수신·인증·가입 및 독립 DB 대사를 완료했다. **API review status는 DENIED이며 일반 발송은 미승인이다. 후속 AWS 메일에서 최종 결정 전 추가 정보를 요청한 것으로 확인되어, 기존 케이스 회신과 운영 활성화를 대기한다.** 공개 프론트/백엔드는 기존 릴리스이며 CPU 서버는 계속 실행한다. 아래 초기 백엔드 검증 기록과 마지막 후속 검증 절을 구분한다.

## 흐름과 API 계약

일반 회원가입만 `메일 요청 → 6자리 번호 확인 → 단일 사용 인증 토큰으로 가입` 순서로 처리한다. 소셜 로그인은 기존 공급자 identity 및 약관 동의 경로를 유지한다. SES의 identity verification API로 회원 주소를 등록하는 방식이 아니라, SES V2 `SendEmail`로 앱의 인증번호를 발송한다.

모든 요청은 HTTPS JSON 본문을 사용한다. 주소·인증번호·토큰을 URL이나 로그, 분석 이벤트에 넣지 않는다. 응답은 `Cache-Control: no-store`다. 아래 값은 전부 예제다.

| 단계 | API | 요청 | 응답 |
|---|---|---|---|
| 필수 여부 | `GET /api/login/email-verification/config` | 없음 | `200 {"required": true}` |
| 발송 | `POST /api/login/email-verification/requests` | `{"email":"person@example.com"}` | `202 {"challengeId":"<64자리 난수>","expiresInSeconds":600,"resendAfterSeconds":60}` |
| 확인 | `POST /api/login/email-verification/confirm` | `{"email":"person@example.com","challengeId":"<반환값>","code":"123456"}` | `200 {"emailVerificationToken":"<64자리 난수>","expiresInSeconds":600}` |
| 가입 | 기존 `POST /api/login/signup` | 기존 아이디·비밀번호·닉네임·이메일·약관 `consent`에 `emailVerificationToken` 추가 | 기존 `201`, 세션 자동 발급 없음 |

가입 예시:

```json
{
  "loginId": "example-user",
  "password": "<사용자가 입력한 비밀번호>",
  "nickName": "example",
  "email": "person@example.com",
  "emailVerificationToken": "<confirm 응답의 토큰>",
  "consent": {
    "version": "<현재 공개 약관 버전>",
    "termsSha256": "<현재 문서 해시>",
    "privacySha256": "<현재 문서 해시>",
    "termsAccepted": true,
    "personalDataAccepted": true,
    "age14Confirmed": true
  }
}
```

`consent`의 버전과 해시는 기존 공개 정책 API에서 받은 현재 값으로 보낸다. 필드명은 기존 `PolicyAcceptance` 계약을 유지한다.

프론트는 번호를 문자열로 취급해 앞자리 0을 보존하고, 이메일을 수정하거나 재발송하면 이전 인증 상태와 토큰을 지워야 한다. 토큰은 화면 메모리에만 보관한다. `config` 조회 실패를 `required=false`로 처리하지 않는다. 서버는 활성화 시 토큰 없는 기존 가입 요청도 거절한다. 인증 UI 배포 후 플래그를 켠다.

## 저장·실패 처리

- 번호 만료 10분, 확인 후 가입 토큰 만료 10분. 가입에서 한 번만 소비한다. 다른 이메일·다른 challenge·재사용·만료는 거절한다.
- 이메일은 공백 제거 및 소문자로 통일한다. 기존 대소문자 이메일 계정도 중복 가입을 막는다. 기존 데이터는 변경하지 않는다. 이메일은 ASCII 주소 형식을 검증하며 국제화 주소를 자동 변환하지 않는다.
- Redis에는 `trader:{signup-email}:*` prefix로 이메일/클라이언트 주소의 HMAC, 번호의 HMAC, 가입 토큰의 SHA-256만 저장한다. 인증번호·이메일·토큰 원문을 저장하지 않는다. 비밀번호는 이 저장소로 보내지 않는다.
- Redis Lua로 발송 제한·확인·소비를 원자적으로 수행한다. 다중 백엔드 인스턴스가 같은 Redis와 HMAC secret을 사용해야 한다. Redis Cluster에서도 같은 slot을 사용한다. 메모리 캐시 대체 경로는 없다.
- 재발송 60초 간격, 이메일별 1시간 5회, 클라이언트 주소별 1시간 20회, 전체 1시간 100회. 시간창은 각 카운터 첫 요청부터 시작하고 1시간 뒤 만료된다. 인증번호는 최대 5회 확인하며, 다섯 번째 오입력 후 번호를 무효화한다. 잘못된 challenge는 유효한 challenge를 소진하지 않는다.
- `getRemoteAddr()`만 사용한다. 임의 `X-Forwarded-For`를 신뢰하지 않는다. 프록시 환경에서는 Tomcat/인프라에서 신뢰할 프록시만 지정해야 한다. 지정하지 않으면 여러 사용자가 프록시 IP의 시간당 20회 제한을 공유할 수 있다.
- SES 실패는 503, Redis 실패도 503이며 가입을 통과시키지 않는다. 발송 실패 시 해당 challenge만 제거한다. 횟수·쿨다운은 남겨 비용과 모호한 발송의 반복을 제한한다. SES SDK 자동 재시도는 끈다. SES API 접수 성공은 수신함 전달 성공의 보장이 아니다.
- 기존 계정 존재 여부를 발송 API 응답으로 조회하지 않는다. 인증 완료 후 기존 가입의 중복 검사가 적용된다.
- Redis 소비와 SQL 가입은 서로 다른 저장소라 단일 트랜잭션이 아니다. 토큰을 소비한 뒤 중복 계정·약관 불일치·SQL 오류로 가입이 실패하면 **다시 이메일 인증**해야 한다. 계정/동의 SQL 트랜잭션은 기존대로 유지한다. 토큰을 복원하거나 두 번 쓰게 하지 않는다.
- 기존 계정의 로그인, 프로필 이메일 변경, 비밀번호 재설정용 인증은 이번 범위에 없다. DB에 별도의 영구 `email_verified` 컬럼도 추가하지 않았다.

오류 응답은 원인에 따라 400 `INVALID_EMAIL`, `EMAIL_VERIFICATION_INVALID_OR_EXPIRED`, `EMAIL_VERIFICATION_REQUIRED`, 429 `EMAIL_VERIFICATION_RATE_LIMITED`, 503 `EMAIL_VERIFICATION_UNAVAILABLE`/`EMAIL_DELIVERY_UNAVAILABLE`다. AWS/Redis 상세 오류와 개인정보는 응답하지 않는다.

## 운영 설정

외부 설정 예시(비밀값은 저장소·이미지에 넣지 않음):

```yaml
trader:
  signup-email:
    enabled: false # 프론트·SES 설정과 수신 테스트 후 true
    region: ap-northeast-2
    from: noreply@revestlab.com # 검증된 발신 identity를 정한 뒤 지정
    hmac-secret: ${SIGNUP_EMAIL_HMAC_SECRET:}
    configuration-set: "" # 준비한 발송 이벤트용 configuration set이 있을 때 지정
```

Spring의 직접 환경변수 바인딩은 `TRADER_SIGNUPEMAIL_ENABLED`, `TRADER_SIGNUPEMAIL_REGION`, `TRADER_SIGNUPEMAIL_FROM`, `TRADER_SIGNUPEMAIL_HMACSECRET`, `TRADER_SIGNUPEMAIL_CONFIGURATIONSET`를 사용한다(하이픈 제거 규칙). HMAC secret은 암호학적 난수 32바이트 이상으로 생성해 외부 비밀 설정에 보관한다. 키를 바꾸면 기존 인증도 무효화된다. 활성화했는데 발신자/지역/secret이 없으면 기동을 실패시킨다. Redis는 기존 `spring.data.redis` 연결을 사용한다.

SES 준비 사항:

1. 서울 리전에서 발신 도메인/주소 identity 확인과 DKIM DNS 검증. [AWS identity 문서](https://docs.aws.amazon.com/ses/latest/dg/creating-identities.html), [DKIM 문서](https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dkim.html).
2. 일반 가입자에게 발송하려면 해당 리전의 production access 확인. sandbox에서는 검증된 수신자 또는 simulator로만 발송할 수 있다. [AWS sandbox 문서](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html).
3. 백엔드 IAM role에 필요한 identity의 `ses:SendEmail`만 부여하고 `ses:FromAddress`를 지정한 발신자로 제한한다. SDK 기본 자격증명 체인을 사용하며 코드에 액세스 키를 넣지 않는다. [AWS IAM 문서](https://docs.aws.amazon.com/ses/latest/dg/control-user-access.html).
4. backend→SES HTTPS 443 및 Redis 접근 확인. SMTP/25 포트는 사용하지 않는다. 반송·불만 이벤트와 발송 지표를 운영 환경에서 연결한다. 초기 백엔드 구현 단계에는 AWS를 변경하지 않았으며, 후속 승인으로 적용한 범위는 아래에 기록한다.
5. 프론트 인증 UI를 연결해 실제 수신·스팸함·재발송·만료를 확인한 뒤 활성화한다. 후속 실제 수신·가입 검증은 아래 절을 참고한다.

## 검증

`emailverification` 패키지 테스트는 발송 내용·입력·장애·설정·가입 전 차단과 별도의 로컬 Redis 7 컨테이너에서 실제 Lua의 만료·횟수·동시성·한 번 소비를 검사한다. 실제 Redis를 연결한 MockMvc로 요청→확인→가입→이메일 변경 거절→재사용 거절을 검증한다. 운영 Redis/DB/SES는 테스트에서 사용하지 않는다.

검증 명령과 최종 결과는 저장소 루트 `output/signup-email-ses-20261101/`에 기록한다. 기존 약관/소셜 로그인/폼 로그인/JWT/비밀번호 회귀 테스트도 함께 수행한다.

2026-11-01 최종 결과: **75건 통과, 실패·오류·skip 0건**. 이 중 새 이메일 인증 검증은 29건(실제 로컬 Redis 검증 11건 포함)이다. Redis를 연결한 HTTP 검증도 메일 발송은 대체 sender, 계정/동의 저장은 모의 서비스로 처리했다. 실제 SES 수신이나 운영 SQL 가입을 검증한 결과로 해석하지 않는다. 테스트 Redis와 Testcontainers 정리 컨테이너는 종료됐으며 AWS와 기존 실행 서버는 변경하지 않았다.

Windows에 설치된 JDK 21에서 Java 17 대상(`--release 17`)으로 컴파일했다. Docker 29와 기존 Testcontainers의 호환을 위해 로컬 실행용 init에 `api.version=1.44`를 지정했다. 운영 Gradle/런타임 설정은 바꾸지 않았다.

백엔드 디렉터리 실행 명령:

```powershell
.\gradlew.bat test `
  --tests 'com.example.trader.emailverification.*' `
  --tests 'com.example.trader.policy.*Test' `
  --tests 'com.example.trader.security.provider.LoginFailure579Test' `
  --tests 'com.example.trader.security.provider.JwtAuthenticationContractTest' `
  --tests 'com.example.trader.security.provider.Password655Test' `
  -I ../output/signup-email-ses-20261101/local-test.init.gradle --console plain
```

## 2026-11-01 프론트 연결·SES 실제 가입 검증

- 이메일 입력 아래에 인증 카드, 6자리 번호 입력, 남은 시간, 재발송 대기, 인증 완료 상태를 추가했다. 인증 설정 조회 실패 시 가입을 막고 재조회 버튼을 제공한다. 이메일 변경·재발송·가입 실패 시 이전 proof를 지우며, 늦게 도착한 이전 이메일 요청의 응답을 적용하지 않는다. proof는 화면 메모리에만 보관한다.
- 발송 실패/횟수 제한에서도 60초 대기를 표시한다. 서버가 재발송을 거절한 경우 기존 번호 확인 화면은 유지하고, 성공한 재발송에서 새 challenge로 교체한다. Enter로 번호 확인을 할 때 회원가입이 함께 제출되지 않도록 했다.
- 새 프론트 테스트 포함 **15건 통과**, 변경 파일 ESLint 오류·경고 0, 빌드 통과. 기존 번들 크기 경고는 남는다. 인증 변경만 역적용한 빌드는 현재 운영 정적 파일 5개와 **바이트 단위 일치**했다. 로컬 전용 랜딩 변경을 제외하고 runtime-config·FREE WAF를 보존한다.
- 사용자 승인으로 서울 SES에 `revestlab.com` Easy DKIM(2048)을 만들고 기존 Route53에 CNAME 3개만 추가했다. DKIM/identity SUCCESS를 확인했다. 발신자는 `noreply@revestlab.com`, configuration set은 `revest-signup`이다. BOUNCE/COMPLAINT account suppression 및 CloudWatch 집계 이벤트를 설정했다.
- 실제 백엔드가 실행 중인 `trader-public-origin-role`에 SendEmail만 허용했다. `ses:FromAddress=noreply@revestlab.com` 조건을 유지하며 도메인·설정 집합과 별도로 검증한 테스트 수신자 identity ARN만 지정했다. 와일드카드/SES 관리 권한은 추가하지 않았다. SDK 실제 오류에서 테스트 수신자 identity 권한이 필요함을 확인해 이 ARN을 추가한 뒤 실제 발송에 성공했다.
- 사용자가 수신 주소를 Gmail에서 Naver로 정정해 이번 작업이 만든 미확인 Gmail SES identity를 제거했다. 정정한 Naver 주소의 SES 확인은 사용자가 직접 완료했다. Gmail로 앱 인증번호/가입을 진행하지 않았다.
- 새 클래스·Lua·SDK 2개만 기존 운영 JAR에 덧붙인 후보 이미지 `trader-backend:signup-email20261101`를 만들었다. 나머지 JAR 항목은 보존했다. 실제 DB/Redis/SES를 쓰는 canary는 loopback 18082이며 컴포넌트 전체 스캔·스케줄러·메시지 소비자를 등록하지 않았다. canary 전용 런처는 운영 JAR에 넣지 않았다. 초기 canary의 설정 마운트/엔티티 검색 범위 오류는 격리된 검증 환경에서만 수정했고 운영 컨테이너는 교체하지 않았다.
- 로컬 프론트 16134→SSM 18182→canary 18082 경로에서 사용자에게 실제 메일을 발송했다. 사용자가 직접 번호 확인·비밀번호 입력·약관 동의·회원가입을 완료했다. 이어 별도 READ ONLY DB 연결로 **계정 1개, Role.USER(1), local identity, 해시 비밀번호, 현재 문서 해시/시각과 일치하는 LOCAL 동의 1개**를 대사했다. 토큰 누락·가짜 토큰·가짜 challenge 거절 3건도 확인했다. 해당 실제 계정은 보존한다.
- canary는 가입 전용이어서 로그인은 운영 [sign-in](https://revestlab.com/sign-in)에서 진행한다. canary의 로그인 실패를 운영 로그인 장애로 해석하지 않는다. 이번에 새 계정의 운영 로그인을 직접 완료했다고 주장하지 않는다.

증거: 저장소 루트 `output/signup-email-production-20261101/`의 `frontend-verification.json`, `baseline-comparison.json`, `ses-status.json`, `ses-sdk-probe-after.json`, `actual-signup-verification.json`, `actual-negative-checks.json`, `backend-stage.json`, `backend-canary.json`, `actual-otp-sent.png`.

SES 프로덕션 신청은 접수 후 **DENIED**다. 일반 주소 발송을 막는 sandbox 상태가 유지되어 공개 필수 인증 배포/활성화를 하지 않았다. 지원 케이스 `179352722300781`의 자동 상세 조회는 `SubscriptionRequiredException`으로 실패했다. [재검토 준비 문서](ses-production-access-review.md)에 미발송 초안을 보관한다. 배포 helper는 SES `ProductionAccessEnabled=true`·실제 가입 대사·백엔드 준비 확인이 없으면 발행을 차단한다. 기존 서버·DB·EBS와 공개 릴리스는 유지한다.

### AWS 안내 해석 정정

사용자가 후속으로 제공한 안내는 자동 초기 검토의 추가 정보 요청이다. API의 DENIED 표시를 최종 거절로 단정하지 않는다. 일반 발송이 아직 허용되지 않는 사실은 그대로이며, 기존 케이스에 6개 요청 항목과 실제 메일 예시를 회신할 단계다. [완성된 미발송 초안](ses-production-access-review.md)에 현재 억제/집계 설정과 검증 결과를 반영했다. 서버 및 운영 릴리스는 변경하지 않았다.
