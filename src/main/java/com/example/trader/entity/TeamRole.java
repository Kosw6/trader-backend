package com.example.trader.entity;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum TeamRole {
    OWNER("O01","OWNER"),MANAGER("M01","MANAGER"), MEMBER("M02","MEMBER");
    //시스템용
    private String code;
    private String role;
    TeamRole(String code, String role){
        this.code = code;
        this.role = role;
    }

    public static TeamRole ofCode(String code){
        return Arrays.stream(TeamRole.values())
                .filter(t -> t.code.equals(code))
                .findAny()
                .orElseThrow();
    }
    public static TeamRole ofRole(String role){
        return Arrays.stream(TeamRole.values())
                .filter(t -> t.role.equals(role))
                .findAny()
                .orElseThrow();
    }
}
