package com.example.trader.common;
import java.security.SecureRandom;

public class InviteCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // 헷갈리는 문자 제거
    private static final char[] CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET[RANDOM.nextInt(CHARSET.length)]);
        }
        return sb.toString();
    }
}

