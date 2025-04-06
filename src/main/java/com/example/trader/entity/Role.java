package com.example.trader.entity;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum Role {
    ADMIN("001","관리자"),USER("002","회원");

    private String code;
    private String roleName;
    Role(String code, String roleName){
        this.code = code;
        this.roleName = roleName;
    }

    public static Role ofCode(String code){//해당 code를 넘기면 맞는 enum객체를 반환해준다(스트림 이용하자)
        return Arrays.stream(Role.values())
                .filter(r -> r.code.equals(code))
                .findAny()
                .orElseThrow();
    }
}
