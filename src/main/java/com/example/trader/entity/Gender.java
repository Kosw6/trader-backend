package com.example.trader.entity;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum Gender {
    Man("001","Man"), Woman("002","Woman");

    private String code;
    private String genderName;
    Gender(String code, String genderName){
        this.code = code;
        this.genderName = genderName;
    }

    public static Gender ofCode(String code){//해당 code를 넘기면 맞는 enum객체를 반환해준다(스트림 이용하자)
        return Arrays.stream(Gender.values())
                .filter(g -> g.code.equals(code))
                .findAny()
                .orElseThrow();
    }
    public static Gender ofGender(String gender){
        return Arrays.stream(Gender.values())
                .filter(g -> g.genderName.equals(gender))
                .findAny()
                .orElseThrow();
    }
}
