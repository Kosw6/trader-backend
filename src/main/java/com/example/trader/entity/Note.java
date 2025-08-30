package com.example.trader.entity;


import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Getter
@Table(name = "note")
@NoArgsConstructor(access = AccessLevel.PROTECTED)//JPA의 지연로딩을 위해 필요함, 빈 객체 생성후 값 주입 방식
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class Note extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    private Long teamId;
    private String subject;
    private String content;
    private String stockSymb;
    private LocalDate noteDate;


    public void setUser(User userId){
        this.user = user;
    }
    public void setTeam(Long teamId){
        this.teamId = teamId;
    }
    public void changeContent(String content){
        this.content = content;
    }
    public void changeSubject(String subject){
        this.subject = subject;
    }
    public void changestockSymb(String stockSymb){
        this.stockSymb = stockSymb;
    }
}
