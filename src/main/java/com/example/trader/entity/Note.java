package com.example.trader.entity;


import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@ToString(exclude = "user")
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

    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @ManyToOne(fetch = FetchType.LAZY,cascade = CascadeType.REMOVE)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY,cascade = CascadeType.REMOVE)
    private Team team;
    private String subject;
    private String content;
    private String stockSymb;

    public void setUser(User user){
        this.user=user;
    }
    public void setTeam(Team team){
        this.team = team;
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
