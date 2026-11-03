package com.example.trader.security.provider;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import com.example.trader.entity.*;
import com.example.trader.repository.UserRepository;
import com.example.trader.service.UserService;

class Password655Test {
    @Test void concurrentHashesCompleteAndRemainValid() throws Exception {
        var jobs=java.util.stream.IntStream.range(0,12).mapToObj(i->java.util.concurrent.CompletableFuture.supplyAsync(
            ()->Password655.matches("burst-"+i,Password655.encode("burst-"+i)))).toList();
        for(var job:jobs) assertThat(job.get(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    @Test void nativeArgonMigrationVectorMatchesSpring() {
        assertThat(Password655.matches("synthetic-한글-🔐",
            "$argon2id$v=19$m=19456,t=2,p=1$AAECAwQFBgcICQoLDA0ODw$AuYDx837hsmq4vNIMu0UYgelpE1gQ43qX0hHuUx26aE")).isTrue();
    }
    @Test void pythonMigrationVectorMatchesSpringEncoder() {
        assertThat(Password655.matches("synthetic-한글-🔐",Password655.PREFIX+
            "000102030405060708090a0b0c0d0e0fd10f32d31e718042c00735b89efe6f9f5cb940d4611a3ea44bdb3cb1e4d4d202")).isTrue();
    }
    @Test void saltedHashAcceptsOriginalOnly() {
        String hash=Password655.encode("synthetic-password");
        assertThat(hash).isNotEqualTo(Password655.encode("synthetic-password"));
        assertThat(Password655.matches("synthetic-password",hash)).isTrue();
        assertThat(Password655.matches("wrong",hash)).isFalse();
        assertThat(Password655.matches(hash,hash)).isFalse();
    }
    @Test void plaintextUnknownNullAndMalformedFailClosed() {
        for(String stored:new String[]{null,"raw", "{noop}raw",Password655.PREFIX+"0".repeat(95),Password655.PREFIX+"z".repeat(96)})
            assertThat(Password655.matches("raw",stored)).isFalse();
        assertThat(Password655.matches(null,Password655.encode("synthetic"))).isFalse();
    }
    @Test void unicodeAndLongPasswordsAreNotTruncated() {
        String raw="한글🔐".repeat(20);String hash=Password655.encode(raw);
        assertThat(Password655.matches(raw,hash)).isTrue();
        assertThat(Password655.matches(raw+"x",hash)).isFalse();
        assertThatThrownBy(()->Password655.encode("x".repeat(4097))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Password655.encode(" ")).isInstanceOf(IllegalArgumentException.class);
    }
    private User user(long id,String password) {return User.builder().id(id).email("synthetic@example.invalid").username("test")
        .loginId("synthetic").nickName("test").password(password).role(Role.USER).build();}
    @Test void signupAlwaysHashesEvenHashShapedInput() {
        var repo=mock(UserRepository.class);var service=new UserService(repo);
        when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        String raw=Password655.encode("test");User u=user(1,raw);service.createUser(u);
        assertThat(u.getPassword()).isNotEqualTo(raw);
        assertThat(Password655.matches(raw,u.getPassword())).isTrue();
    }
    @Test void existingProviderIdentityIsPreservedWithoutEmailRebinding() {
        var repo=mock(UserRepository.class);User u=user(1,"old");u.linkOAuth("kakao","synthetic-id");
        when(repo.findByProviderAndProviderId("kakao","synthetic-id")).thenReturn(Optional.of(u));
        assertThat(new UserService(repo).upsertOAuthUser("kakao","synthetic-id","changed@example.invalid","changed")).isSameAs(u);
        assertThat(u.getEmail()).isEqualTo("synthetic@example.invalid");verify(repo,never()).findByEmail(any());
    }
    @Test void emailOnlyCollisionNeverLinksAccounts() {
        var repo=mock(UserRepository.class);User u=user(1,"old");
        when(repo.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        assertThatThrownBy(()->new UserService(repo).upsertOAuthUser("kakao","other",u.getEmail(),"test"))
            .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
        assertThat(u.getProvider()).isNull();verify(repo,never()).save(any());
    }
    @Test void updatePreservesProviderAndHashesNewPassword() {
        var repo=mock(UserRepository.class);User u=user(1,"old");u.linkOAuth("google","synthetic-id");
        when(repo.findById(1L)).thenReturn(Optional.of(u));when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        new UserService(repo).updateUser(user(1,"new-synthetic"));
        assertThat(Password655.matches("new-synthetic",u.getPassword())).isTrue();
        assertThat(u.getProviderId()).isEqualTo("synthetic-id");
    }
    @Test void updateRejectsBodyIdentityMismatchBeforeServiceCall() {
        var service=mock(UserService.class);var controller=new com.example.trader.controller.UserController(service);
        var input=new com.example.trader.dto.RequestUserDto(2L,"synthetic@example.invalid","test",null,"test","pw",null,"test");
        assertThatThrownBy(()->controller.updateUser(1L,input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(service);
    }
    @Test void entityAndDtoNeverSerializeOrPrintPassword() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var u=user(1,"sensitive-synthetic-value");
        var dto=com.example.trader.dto.ResponseUserDto.of(u);
        assertThat(mapper.writeValueAsString(u)).doesNotContain("sensitive-synthetic-value","\"password\"");
        assertThat(mapper.writeValueAsString(dto)).doesNotContain("sensitive-synthetic-value","\"password\"");
        assertThat(dto.toString()).doesNotContain("sensitive-synthetic-value");
    }
    @Test void authenticationProviderAcceptsHashAndRejectsHashAsPassword() {
        var details=mock(org.springframework.security.core.userdetails.UserDetailsService.class);
        var u=user(1,Password655.encode("synthetic-original"));
        var context=new com.example.trader.security.details.UserContext(com.example.trader.dto.ResponseUserDto.of(u),java.util.List.of());
        when(details.loadUserByUsername("synthetic")).thenReturn(context);
        var provider=new FormAuthenticationProvider(details);
        assertThat(provider.authenticate(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("synthetic","synthetic-original")).isAuthenticated()).isTrue();
        assertThatThrownBy(()->provider.authenticate(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("synthetic",u.getPassword())))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
}
