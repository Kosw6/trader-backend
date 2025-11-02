package com.example.trader.entity;

import com.example.trader.dto.RequestUserDto;
import com.example.trader.entity.base.BaseTimeEntity;

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
    private String email;
    @NonNull
    private String username;
    private Integer age;
    @NonNull
    private String loginId;
    @NonNull
    private String password;
    private Gender gender;
    @NonNull
    private String nickName;
    @NonNull
    private Role role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
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

    public void addNotes(List<Note> notes){
        for (Note note : notes) {
            note.setUser(this);
        }
        this.notes.addAll(notes);
    }
    public void removeNote(Note note){
        notes.remove(note);
        note.setUser(null);
    }
    // Getter, Setter, 편의 메서드
    public void addTeam(UserTeam userTeam) {
        userTeams.add(userTeam);
        userTeam.setUser(this);
    }

    public void removeTeam(UserTeam userTeam) {
        userTeams.remove(userTeam);
        userTeam.setUser(null);
    }
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
    public User changeEmailUserName(String email,String username){
        return User.builder()
                .username(username)
                .role(this.role)
                .email(email)
                .age(this.age)
                .providerId(this.providerId)
                .provider(this.provider)
                .gender(this.gender)
                .nickName(this.nickName)
                .loginId(this.loginId)
                .password(this.password)
                .userTeams(this.userTeams)
                .id(this.id).build();
    }
}
