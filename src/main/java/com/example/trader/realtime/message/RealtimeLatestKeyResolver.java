package com.example.trader.realtime.message;

import com.example.trader.ws.raw.dto.RawCursorMessage;
import org.springframework.stereotype.Component;

@Component
public class RealtimeLatestKeyResolver {

    public String resolve(RawCursorMessage msg) {
        if ("CURSOR".equals(msg.type())) {
            return msg.type() + ":" + msg.userId();
        }

        if ("DRAG_PREVIEW".equals(msg.type())) {
            return msg.type() + ":" + msg.userId() + ":" +
                    (msg.nodeId() != null ? msg.nodeId() : 0L);
        }

        return msg.type() + ":" + msg.userId();
    }
}
