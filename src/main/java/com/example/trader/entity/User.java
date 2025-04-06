package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
@ToString
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)//JPA의 지연로딩을 위해 필요함, 빈 객체 생성후 값 주입 방식
@AllArgsConstructor
@EqualsAndHashCode
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
    private String nick_name;
    @NonNull
    private Role role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserTeam> userTeams = new ArrayList<>();
    private String provider;
    private String providerId;
    @OneToMany(mappedBy = "user",orphanRemoval = true)//고아객체 삭제 + note객체의 user필드와 연결
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
}
