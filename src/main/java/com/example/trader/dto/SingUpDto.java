package com.example.trader.dto;

public record SingUpDto(String loginId,String password, String userName,
                        String email,String nickName,String gender,Integer age, com.example.trader.policy.PolicyAcceptance consent,
                        String emailVerificationToken) {
    public SingUpDto(String loginId, String password, String userName, String email, String nickName,
                     String gender, Integer age, com.example.trader.policy.PolicyAcceptance consent) {
        this(loginId, password, userName, email, nickName, gender, age, consent, null);
    }
    public SingUpDto{
        if(loginId == null||loginId.isBlank()){
            throw new IllegalArgumentException("loginID cannot be null");
        }
        if(password == null||password.isBlank()){
            throw new IllegalArgumentException("password cannot be null");
        }
        if(email == null||email.isBlank()){
            throw new IllegalArgumentException("email cannot be null");
        }
        if(nickName == null||nickName.isBlank()){
            throw new IllegalArgumentException("nickName cannot be null");
        }
        // Legacy clients may send these fields; do not collect them in new accounts.
        userName=nickName; age=null; gender=null;
    }
}
