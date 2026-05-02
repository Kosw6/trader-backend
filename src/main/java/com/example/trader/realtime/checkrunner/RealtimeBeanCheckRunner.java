package com.example.trader.realtime.checkrunner;

import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.VolatilePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeBeanCheckRunner implements ApplicationRunner {

    private final RealtimePublisher realtimePublisher;
    private final ReliablePublisher reliablePublisher;
    private final VolatilePublisher volatilePublisher;

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("RealtimePublisher = " + realtimePublisher.getClass());
        System.out.println("ReliablePublisher = " + reliablePublisher.getClass());
        System.out.println("VolatilePublisher = " + volatilePublisher.getClass());
    }
}
