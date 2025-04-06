package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String teamName;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserTeam> userTeams = new ArrayList<>();

    // Getter, Setter, 편의 메서드
    public void addUser(UserTeam userTeam) {
        userTeams.add(userTeam);
        userTeam.setTeam(this);
    }

    public void removeUser(UserTeam userTeam) {
        userTeams.remove(userTeam);
        userTeam.setTeam(null);
    }
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> noteList;
    public void addNotes(List<Note> notes){
        for (Note note : notes) {
            note.setTeam(this);
        }
        this.noteList.addAll(notes);
    }
    public void removeNote(Note note){
        noteList.remove(note);
        note.setTeam(null);
    }
}
