package com.example.trader.service.event;

/** 업무 계층이 요구하는 전달 의무 기록 경계. */
public interface NodeChangeEventStager {
    /**
     * 활성 DB 트랜잭션에 변경 이벤트를 기록한다. 실패는 호출자에게 전파한다.
     * 정상 반환은 커밋 또는 외부 전달 완료를 뜻하지 않는다.
     */
    void stage(NodeChange change);
}
