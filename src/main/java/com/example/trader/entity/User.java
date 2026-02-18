package com.example.trader.entity;

import com.example.trader.dto.RequestUserDto;
import com.example.trader.entity.base.BaseTimeEntity;

import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)//JPA의 지연로딩을 위해 필요함, 빈 객체 생성후 값 주입 방식
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NonNull
    @Column(nullable = false)
    private String email;
    @NonNull
    @Column(nullable = false)
    private String username;
    private Integer age;
    @NonNull
    @Column(nullable = false)
    private String loginId;
    @NonNull
    @Column(nullable = false)
    private String password;
    private Gender gender;
    @NonNull
    @Column(nullable = false)
    private String nickName;

    //todo:나중에 String으로 변환 @Enumerated(EnumType.STRING)
    @NonNull
    private Role role;

    @Builder.Default
    @OneToMany(mappedBy = "user", orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<UserTeam> userTeams = new ArrayList<>();
    private String provider;
    private String providerId;
    @OneToMany(mappedBy = "user",cascade = CascadeType.ALL,orphanRemoval = true)//고아객체 삭제 + note객체의 user필드와 연결
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final List<Note> notes = new ArrayList<>();

//    public void addNotes(List<Note> notes){
//        for (Note note : notes) {
//            note.setUser(this);
//        }
//        this.notes.addAll(notes);
//    }
//    public void removeNote(Note note){
//        notes.remove(note);
//        note.setUser(null);
//    }
    public static User of(RequestUserDto userDto){
        return User.builder().username(userDto.username()).
                email(userDto.email()).
                role(Role.USER).
                loginId(userDto.loginId()).
                gender(Gender.ofGender(userDto.gender())).
                age(userDto.age()).
                password(userDto.password()).
                nickName(userDto.nickName()).
                id(userDto.id()).build();

    }
    public void linkOAuth(String provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
    }

    public void fillProfileIfEmpty(String name) {
        if (this.username == null || this.username.isBlank()) this.username = name;
        if (this.nickName == null || this.nickName.isBlank()) this.nickName = name;
    }

    public User changeUserRole(Role role){
        this.role = role;
        return this;
    }

    //서비스 레이어에서 DB조회 후 중복 체크
    public void changeLoginId(String unDuplicatedLoginId) {
        if (unDuplicatedLoginId == null || unDuplicatedLoginId.isBlank()) {
            throw new BaseException(BaseResponseStatus.INVALID_USER, "로그인 아이디가 비어있습니다.");
        }
        this.loginId = unDuplicatedLoginId;
    }

    //서비스 레이어에서 중복 체크
    public void changeNickName(String unDuplicatedNickName) {
        if (unDuplicatedNickName == null || unDuplicatedNickName.isBlank()) {
            throw new BaseException(BaseResponseStatus.INVALID_USER, "닉네임이 비어있습니다.");
        }
        this.nickName = unDuplicatedNickName;
    }

    public void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new BaseException(BaseResponseStatus.INVALID_USER, "비밀번호가 비어있습니다.");
        }
        this.password = encodedPassword;
    }


}
