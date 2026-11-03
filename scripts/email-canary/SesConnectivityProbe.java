package com.example.trader.canary;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/** One explicitly labelled delivery probe; contains no OTP, token or account data. */
public class SesConnectivityProbe {
    public static void main(String[] args) {
        boolean credentials=false;
        try (var provider=DefaultCredentialsProvider.create()) {
            provider.resolveCredentials();credentials=true;
            try(var ses=SesV2Client.builder().region(Region.AP_NORTHEAST_2)
                    .credentialsProvider(provider).httpClientBuilder(UrlConnectionHttpClient.builder())
                    .overrideConfiguration(c->c.apiCallTimeout(java.time.Duration.ofSeconds(10))
                            .retryPolicy(software.amazon.awssdk.core.retry.RetryPolicy.none())).build()) {
                ses.sendEmail(SendEmailRequest.builder().fromEmailAddress("noreply@revestlab.com")
                        .configurationSetName("revest-signup")
                        .destination(Destination.builder().toAddresses("warandmek162211@naver.com").build())
                        .content(EmailContent.builder().simple(Message.builder()
                                .subject(Content.builder().charset("UTF-8").data("[Revest Lab] 이메일 연결 확인").build())
                                .body(Body.builder().text(Content.builder().charset("UTF-8")
                                        .data("이 메일은 SES 연결 확인용입니다. 인증번호가 없으며 가입이 진행되지 않습니다. 실제 가입 인증번호는 검증 화면에서 별도로 요청해주세요.")
                                        .build()).build()).build()).build()).build());
                System.out.println("{\"credentialsResolved\":true,\"sendAccepted\":true}");
            }
        } catch (SesV2Exception ex) {
            String code=ex.awsErrorDetails()==null?"unknown":ex.awsErrorDetails().errorCode();
            if(code==null || !code.matches("[A-Za-z0-9_.-]+")) code="unknown";
            String message=ex.awsErrorDetails()==null?"":String.valueOf(ex.awsErrorDetails().errorMessage());
            var resources=java.util.regex.Pattern.compile("(?:identity|configuration-set)/[A-Za-z0-9@._-]+").matcher(message);
            String resource=resources.find()?resources.group():"unknown";
            var roles=java.util.regex.Pattern.compile("assumed-role/([A-Za-z0-9_.-]+)/").matcher(message);
            String role=roles.find()?roles.group(1):"unknown";
            System.out.println("{\"credentialsResolved\":"+credentials+",\"sendAccepted\":false,\"status\":"+ex.statusCode()+",\"errorCode\":\""+code+"\",\"deniedResource\":\""+resource+"\",\"role\":\""+role+"\",\"explicitDeny\":"+message.contains("explicit deny")+",\"missingIdentityPolicy\":"+message.contains("no identity-based policy")+"}");
        } catch (Throwable ex) {
            System.out.println("{\"credentialsResolved\":"+credentials+",\"sendAccepted\":false,\"errorType\":\""+ex.getClass().getSimpleName()+"\"}");
        }
    }
}
