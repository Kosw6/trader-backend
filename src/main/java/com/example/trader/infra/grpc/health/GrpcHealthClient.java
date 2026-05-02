package com.example.trader.infra.grpc.health;

import org.springframework.stereotype.Component;

@Component
public class GrpcHealthClient {

    public boolean check() {
        // TODO: gRPC health checking protocol 붙이기 전 임시
        return true;
    }
}