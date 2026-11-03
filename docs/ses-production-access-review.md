# SES production access 추가 정보 회신

2026-11-01 현재 API의 review status는 `DENIED`, ProductionAccessEnabled는 false다. 그러나 사용자가 제공한 AWS 메일은 **자동 초기 검토 후 추가 정보 요청**이며 “before a final decision can be made”라고 명시한다. 앞서 API 상태만으로 최종 거절이라고 설명한 해석을 정정한다. 기존 케이스 `179352722300781`에 아래 6개 항목을 회신해 검토를 이어간다. 새 신청을 반복 제출하지 않는다.

서울 발신 `revestlab.com`/DKIM과 테스트 수신자는 확인 완료다. 실제 수신·인증·가입 및 독립 DB 대사를 완료했고, 현재 억제 사유 BOUNCE/COMPLAINT와 활성 CloudWatch 이벤트 7종을 다시 읽기 확인했다. 증거는 `output/signup-email-production-20261101/support-reply-settings-read.json`이다.

**일반 가입자 발송 승인과 공개 필수 인증 활성화는 아직 완료되지 않았다.** Support API 상세 읽기는 현재 계정에서 SubscriptionRequiredException을 반환했다. 이 때문에 지원 케이스 내용을 사용자가 직접 제공했다.

[AWS Support Console](https://support.console.aws.amazon.com/support/home)에서 기존 케이스에 답한다. 아래는 **미발송 초안**이다. 지원 메시지는 자동 전송하지 않았다. 발송량 20–50통/일, 600–1,500통/30일은 초기 20명을 기준으로 한 계획 추정치이며 실제 사용량이나 코드의 일일 상한이라고 주장하지 않는다.

```text
Hello Amazon SES team,

Thank you for your follow-up. Here are the requested details for case 179352722300781, in ap-northeast-2 (Asia Pacific, Seoul).

1. Website/application URL
https://revestlab.com
Revest Lab is a stock research and investment-note application where users review financial information and keep their own research notes.

2. Email type
TRANSACTIONAL only: email verification codes requested during account registration. We are not requesting permission for newsletters, promotions, or bulk marketing emails.
Sender: noreply@revestlab.com

3. Expected volume
Our initial audience is approximately 20 users. Our launch planning estimate is about 20-50 messages per day, or 600-1,500 messages per 30-day month, including verification resends. These are estimates, not an enforced daily limit. Actual usage may be lower. We are requesting production access, not a sending-quota increase.

4. Recipient source and consent
A visitor enters their own email address on our signup form and clicks the button to request a verification code. We send only to that user-supplied address in response to the request. We do not use purchased, rented, scraped, or third-party mailing lists.
Account creation requires successful code verification and the user's explicit acceptance of the required service/privacy terms. This does not subscribe them to marketing.
Codes expire after 10 minutes and allow at most five verification attempts. Our application enforces a 60-second resend cooldown, five sends per email address per hour, twenty per client address per hour, and one hundred total sends per hour. Signup proof is email-bound and single-use.

5. Bounce and complaint handling
SES account-level suppression is enabled for both BOUNCE and COMPLAINT. We use the configuration set "revest-signup" with enabled CloudWatch aggregate events for SEND, DELIVERY, BOUNCE, COMPLAINT, REJECT, RENDERING_FAILURE, and DELIVERY_DELAY.
We will review delivery/reputation metrics, investigate abnormal rates or abuse, and suspend signup email sending if necessary. We do not send unsolicited marketing or log verification codes/message bodies in our application.

6. Sample email
From: noreply@revestlab.com
Subject: [Revest Lab] 회원가입 이메일 인증번호

회원가입 인증번호는 123456 입니다.
10분 이내에 입력해주세요.
인증번호를 다른 사람에게 알려주지 마세요. 요청하지 않았다면 이 메일을 무시해주세요.

The number above is an illustrative example, not a valid code. The actual email contains a newly generated six-digit code and no marketing content.

Verified identity and testing
revestlab.com is verified in ap-northeast-2, and Easy DKIM verification is successful with a 2048-bit key. We have successfully tested actual delivery to a verified sandbox recipient, code verification, and account creation. Public required-email signup remains disabled until production access is granted.

Please let us know if you need any additional details to complete your review.

Thank you,
Revest Lab

```

운영 활성화 순서: 기존 케이스에 추가 정보 회신 → SES ProductionAccessEnabled=true 독립 확인 → 검증된 백엔드/프론트 릴리스 적용 → 실제 가입·동의 기록 및 미인증 차단 재확인. 기존 컨테이너·정적 릴리스는 롤백용으로 보존하고 CPU 서버 실행 상태를 유지한다.
