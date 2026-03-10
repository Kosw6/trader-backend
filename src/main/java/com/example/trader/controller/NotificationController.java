package com.example.trader.controller;

import com.example.trader.dto.ResponseNotifycationDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NotifycationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
public class NotificationController {
    private final NotifycationService service;
    @GetMapping("/unRead")
    public List<ResponseNotifycationDto> getUnReadAllNoti(@AuthenticationPrincipal UserContext user){
        return service.getUnReadNotifycationList(user.getUserDto().getId());
    }

    @GetMapping("/all")
    public List<ResponseNotifycationDto> getReadAllNoti(@AuthenticationPrincipal UserContext user){
        return service.getNotifycationList(user.getUserDto().getId());
    }

//    @GetMapping("/mark")
//    public List<ResponseNotifycationDto> markNoty(@AuthenticationPrincipal UserContext user){
//        return service.
//    }
}
