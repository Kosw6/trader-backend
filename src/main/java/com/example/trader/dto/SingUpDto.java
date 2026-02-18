package com.example.trader.dto;

public record SingUpDto(String loginId,String password, String userName,
                        String email,String nickName,String gender,Integer age) {
    public SingUpDto{
        if(loginId == null||loginId.isBlank()){
            throw new IllegalArgumentException("loginID cannot be null");
        }
        if(password == null||password.isBlank()){
            throw new IllegalArgumentException("password cannot be null");
        }
        if(userName == null||userName.isBlank()){
            throw new IllegalArgumentException("userName cannot be null");
        }
        if(email == null||email.isBlank()){
            throw new IllegalArgumentException("email cannot be null");
        }
        if(nickName == null||nickName.isBlank()){
            throw new IllegalArgumentException("nickName cannot be null");
        }
        if(age == null){
            throw new IllegalArgumentException("age cannot be null");
        }
    }
}
