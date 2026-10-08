package com.example.trader.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiAvailabilityContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void readyRequiresBothHttpAndExplicitReadiness() throws Exception {
        assertEquals("READY", state(200,"ready","READY"));
        assertEquals("UNAVAILABLE", state(503,"ready","READY"));
        assertEquals("UNAVAILABLE", state(200,"ready",""));
        assertEquals("UNAVAILABLE", state(200,"not_ready","READY"));
    }

    @Test void preservesPreparationAndConfirmedStopButNotArbitraryStates() throws Exception {
        for (String value : new String[]{"STOPPED","STARTING","WARMING","UNAVAILABLE"})
            assertEquals(value, state(503,"not_ready",value));
        assertEquals("UNAVAILABLE", state(503,"not_ready","UNKNOWN"));
        assertEquals("UNAVAILABLE", state(502,"not_ready","STOPPED"));
    }

    private Object state(int http,String status,String value) throws Exception {
        return AiAvailabilityController.decodeState(mapper,http,
                "{\"status\":\""+status+"\",\"state\":\""+value+"\"}").get("state");
    }
}
