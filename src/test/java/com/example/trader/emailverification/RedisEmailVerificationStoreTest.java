package com.example.trader.emailverification;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RedisEmailVerificationStoreTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withCommand("redis-server","--save","","--appendonly","no");
    static LettuceConnectionFactory connection;
    static StringRedisTemplate template;
    RedisEmailVerificationStore store;
    final String prefix=RedisEmailVerificationStore.PREFIX;
    @BeforeAll static void connect() {
        connection=new LettuceConnectionFactory(redis.getHost(),redis.getMappedPort(6379));
        connection.afterPropertiesSet();connection.start();template=new StringRedisTemplate(connection);
    }
    @AfterAll static void disconnect() { if(connection!=null) connection.destroy(); }
    @BeforeEach void reset() {
        // Dedicated disposable test container only. Never target an application Redis.
        try(var c=connection.getConnection()){c.serverCommands().flushDb();}
        store=new RedisEmailVerificationStore(template);
    }
    @Test void oneTimeProofAndEmailBinding() {
        assertTrue(store.issue("email","client","challenge","mac"));
        assertFalse(store.consume("email","proof"));
        assertFalse(store.confirm("other","challenge","mac","proof"));
        assertFalse(store.confirm("email","wrong-challenge","mac","proof"));
        assertTrue(store.confirm("email","challenge","mac","proof"));
        assertFalse(store.confirm("email","challenge","mac","different-proof"));
        assertFalse(store.consume("email","wrong"));
        assertTrue(store.consume("email","proof"));
        assertFalse(store.consume("email","proof"));
    }
    @Test void fifthWrongGuessInvalidatesEvenCorrectCode() {
        assertTrue(store.issue("email","client","challenge","mac"));
        for(int i=0;i<5;i++) assertFalse(store.confirm("email","challenge","wrong","proof"));
        assertFalse(store.confirm("email","challenge","mac","proof"));
        assertFalse(template.hasKey(prefix+"state:email"));
    }
    @Test void correctFifthGuessAllowed() {
        assertTrue(store.issue("email","client","challenge","mac"));
        for(int i=0;i<4;i++) assertFalse(store.confirm("email","challenge","wrong","proof"));
        assertTrue(store.confirm("email","challenge","mac","proof"));
    }
    @Test void resendInvalidatesOldChallengeAndLateFailureCannotCancelNewOne() {
        assertTrue(store.issue("email","client","old","oldmac"));
        assertFalse(store.issue("email","client","new","newmac"));
        template.delete(prefix+"cooldown:email");
        assertTrue(store.issue("email","client","new","newmac"));
        store.cancel("email","old");
        assertFalse(store.confirm("email","old","oldmac","proof"));
        assertTrue(store.confirm("email","new","newmac","proof"));
    }
    @Test void expiryAndNoPersistentSecrets() {
        assertTrue(store.issue("email","client","challenge","mac"));
        assertTrue(template.getExpire(prefix+"state:email")>590);
        assertTrue(template.getExpire(prefix+"cooldown:email")>50);
        assertTrue(template.getExpire(prefix+"email-limit:email")>3500);
        template.expire(prefix+"state:email",Duration.ZERO);
        assertFalse(store.confirm("email","challenge","mac","proof"));
        template.delete(prefix+"cooldown:email");
        assertTrue(store.issue("email","client","challenge","mac"));
        assertTrue(store.confirm("email","challenge","mac","proof"));
        assertFalse(template.opsForHash().hasKey(prefix+"state:email","codeMac"));
        assertTrue(template.getExpire(prefix+"state:email")>590);
        template.expire(prefix+"state:email",Duration.ZERO);
        assertFalse(store.consume("email","proof"));
    }
    @Test void perEmailHourlyLimitCannotBeBypassedWithResendOrNewClient() {
        for(int i=0;i<5;i++) {
            template.delete(prefix+"cooldown:email");
            assertTrue(store.issue("email","client"+i,"challenge","mac"));
        }
        template.delete(prefix+"cooldown:email");
        assertFalse(store.issue("email","another-client","challenge","mac"));
    }
    @Test void clientAndGlobalHourlyCapsApplyAcrossDifferentEmails() {
        for(int i=0;i<20;i++) assertTrue(store.issue("email"+i,"client","challenge","mac"));
        assertFalse(store.issue("email21","client","challenge","mac"));
        for(int i=20;i<100;i++) assertTrue(store.issue("email"+i,"client"+i,"challenge","mac"));
        assertFalse(store.issue("last","newclient","challenge","mac"));
    }
    @Test void concurrentIssuanceOnlySendsOnce() throws Exception {
        assertEquals(1,parallel(()->store.issue("email","client","challenge","mac")));
    }
    @Test void concurrentConfirmationProducesOneProof() throws Exception {
        assertTrue(store.issue("email","client","challenge","mac"));
        assertEquals(1,parallel(()->store.confirm("email","challenge","mac","proof")));
    }
    @Test void concurrentSignupsConsumeProofExactlyOnce() throws Exception {
        assertTrue(store.issue("email","client","challenge","mac"));
        assertTrue(store.confirm("email","challenge","mac","proof"));
        assertEquals(1,parallel(()->store.consume("email","proof")));
    }
    @Test void wholeHttpFlowUsesRealRedisAndRejectsChangedEmailAndReplay() throws Exception {
        var sentCode=new java.util.concurrent.atomic.AtomicReference<String>();
        var service=new EmailVerificationService(new EmailVerificationProperties(true,"ap-northeast-2","noreply@example.invalid","s".repeat(40),""),
                store,(email,code)->sentCode.set(code));
        var policy=org.mockito.Mockito.mock(com.example.trader.policy.PolicyConsentService.class);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new EmailVerificationController(service),new com.example.trader.controller.loginController(null,null,null,policy,service))
                .setControllerAdvice(new EmailVerificationExceptionHandler()).build();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var requested=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login/email-verification/requests")
                .contentType("application/json").content("{\"email\":\"person@example.com\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()).andReturn();
        String challenge=mapper.readTree(requested.getResponse().getContentAsString()).get("challengeId").asText();
        var confirmed=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login/email-verification/confirm")
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of(
                        "email","person@example.com","challengeId",challenge,"code",sentCode.get()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String proof=mapper.readTree(confirmed.getResponse().getContentAsString()).get("emailVerificationToken").asText();
        var body=new java.util.HashMap<String,Object>(java.util.Map.of("loginId","fixture","password","fixture-password",
                "email","other@example.com","nickName","fixture","emailVerificationToken",proof));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login/signup")
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(policy);
        body.put("email","PERSON@example.com");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login/signup")
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/login/signup")
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        org.mockito.Mockito.verify(policy,org.mockito.Mockito.times(1)).register(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.isNull());
    }
    private long parallel(Callable<Boolean> task) throws Exception {
        var pool=Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<12;i++) tasks.add(task);
            long successes=0;for(var future:pool.invokeAll(tasks)) if(future.get())successes++;
            return successes;
        } finally {pool.shutdownNow();}
    }
}
