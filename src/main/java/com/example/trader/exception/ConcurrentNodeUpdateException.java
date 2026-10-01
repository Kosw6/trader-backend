package com.example.trader.exception;

/**
 * 충돌 검증 이후 flush 시점에 다른 트랜잭션의 Node 갱신을 발견했음을 나타낸다.
 */
public class ConcurrentNodeUpdateException extends RuntimeException {

    public ConcurrentNodeUpdateException(Throwable cause) {
        super("Node 저장 중 새로운 동시 변경을 감지했습니다.", cause);
    }
}
