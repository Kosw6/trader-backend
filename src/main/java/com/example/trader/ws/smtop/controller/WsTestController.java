package com.example.trader.ws.smtop.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WsTestController {

    @MessageMapping("/ping")
    @SendTo("/topic/ping")
    public String ping(String msg, Principal principal) {
        String who = (principal == null) ? "anonymous" : principal.getName();
        System.out.println("ping from: " + who + ", payload=" + msg);
        return msg;
    }
}
