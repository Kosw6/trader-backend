package com.example.trader.emailverification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SesVerificationMailSenderTest {
    @Test void sendsUtf8TransactionalMessageThroughSesV2WithoutRegistrationSecrets() {
        var client=mock(SesV2Client.class);
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();beans.registerSingleton("ses",client);
        new SesVerificationMailSender(beans.getBeanProvider(SesV2Client.class),
                new EmailVerificationProperties(true,"ap-northeast-2","noreply@example.com","s".repeat(40),"transactional"))
                .send("person@example.com","123456");
        var request=ArgumentCaptor.forClass(SendEmailRequest.class);verify(client).sendEmail(request.capture());
        assertEquals("noreply@example.com",request.getValue().fromEmailAddress());
        assertEquals(java.util.List.of("person@example.com"),request.getValue().destination().toAddresses());
        assertEquals("UTF-8",request.getValue().content().simple().body().text().charset());
        assertTrue(request.getValue().content().simple().body().text().data().contains("123456"));
        assertEquals("transactional",request.getValue().configurationSetName());
    }
}
