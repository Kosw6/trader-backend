package com.example.trader.ai;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiSecurityChainTest {
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({AiSecurityConfig.class,AiCursorCodec.class,Probe.class})
    static class Config {}
    @RestController static class Probe {
        @GetMapping("/internal/ai/indexing/sources/NOTE/1") String read() {return "ok";}
    }
    @Test void actual_security_chain_does_not_inherit_internal_permit_all() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                    "ai.internal.enabled","true","ai.internal.retrieval-key","r".repeat(43),
                    "ai.internal.indexing-key","i".repeat(43),"ai.internal.cursor-kid","test",
                    "ai.internal.cursor-key",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]))));
            context.register(Config.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/internal/ai/indexing/sources/NOTE/1")).andExpect(status().isUnauthorized());
            mvc.perform(get("/internal/ai/indexing/sources/NOTE/1").header("Authorization","Bearer "+"r".repeat(43))).andExpect(status().isForbidden());
            mvc.perform(get("/internal/ai/indexing/sources/NOTE/1").header("Authorization","Bearer "+"i".repeat(43))).andExpect(status().isOk());
        }
    }
}
