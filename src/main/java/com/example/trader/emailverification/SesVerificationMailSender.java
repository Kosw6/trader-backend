package com.example.trader.emailverification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Component
public class SesVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<SesV2Client> client;
    private final EmailVerificationProperties properties;

    public SesVerificationMailSender(ObjectProvider<SesV2Client> client, EmailVerificationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override public void send(String email, String code) {
        var request = SendEmailRequest.builder().fromEmailAddress(properties.from())
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(Content.builder().charset("UTF-8").data("[Revest Lab] 회원가입 이메일 인증번호").build())
                        .body(Body.builder().text(Content.builder().charset("UTF-8")
                                .data("회원가입 인증번호는 " + code + " 입니다.\n10분 이내에 입력해주세요.\n"
                                        + "인증번호를 다른 사람에게 알려주지 마세요. 요청하지 않았다면 이 메일을 무시해주세요.")
                                .build()).build()).build()).build());
        if (properties.configurationSet() != null && !properties.configurationSet().isBlank())
            request.configurationSetName(properties.configurationSet());
        client.getObject().sendEmail(request.build());
    }
}
