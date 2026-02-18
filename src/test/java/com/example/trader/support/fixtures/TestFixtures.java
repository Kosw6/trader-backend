package com.example.trader.support.fixtures;

import com.example.trader.entity.*;
import org.hibernate.mapping.Join;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class TestFixtures {
    private TestFixtures() {}

    private static final AtomicInteger USER_SEQ = new AtomicInteger(1);
    private static final AtomicInteger TEAM_SEQ = new AtomicInteger(1);
    private static final AtomicInteger USER_TEAM_SEQ = new AtomicInteger(1);
    private static final AtomicInteger JQ_SEQ = new AtomicInteger(1);
    private static final AtomicInteger NODE_SEQ = new AtomicInteger(1);
    private static final AtomicInteger NOTE_SEQ = new AtomicInteger(1);
    private static final AtomicInteger DIR_SEQ = new AtomicInteger(1);
    private static final AtomicInteger PAGE_SEQ = new AtomicInteger(1);
    private static final AtomicInteger EDGE_SEQ = new AtomicInteger(1);


    public static User user() {
        int n = USER_SEQ.getAndIncrement();
        User user = User.builder()
                .username("홍길동" + n)
                .email("user" + n + "@test.com")
                .age(20)
                .loginId("login" + n)
                .password("pw1234!")
                .gender(Gender.MALE)
                .role(Role.USER)
                .nickName("nick" + n).build();
        return user;
    }

    public static User userWithId() {
        int n = USER_SEQ.getAndIncrement();
        User user = User.builder()
                .id(Long.valueOf(n))
                .username("홍길동" + n)
                .email("user" + n + "@test.com")
                .age(20)
                .loginId("login" + n)
                .password("pw1234!")
                .gender(Gender.MALE)
                .role(Role.USER)
                .nickName("nick" + n).build();
        return user;
    }

    public static Team team() {
        int n = TEAM_SEQ.getAndIncrement();
        return Team.builder()
                .teamName("team"+n)
                .code("code"+n)
                .build();
    }
    public static Team teamWithId() {
        int n = TEAM_SEQ.getAndIncrement();
        return Team.builder()
                .id(Long.valueOf(n))
                .teamName("team"+n)
                .code("code"+n)
                .build();
    }
    public static UserTeam userTeam(User user, Team team) {
        int n = USER_TEAM_SEQ.getAndIncrement();
        return new UserTeam(Long.valueOf(n),user,team,TeamRole.OWNER);
    }
    public static JoinRequest joinRequestWithId(User user, Team team){
        int n = JQ_SEQ.getAndIncrement();
        JoinRequest req = JoinRequest.create(user,team);
        ReflectionTestUtils.setField(req, "id",Long.valueOf(n));
        return req;
    }
    public static Node node(Page page) {
        int n = NODE_SEQ.getAndIncrement();
        Node node = Node.builder()
                .x(100 + n)
                .y(200 + n)
                .subject("node-subject-" + n)
                .content("node-content-" + n + " lorem ipsum dolor sit amet")
                .symb("AAPL")
                .recordDate(LocalDate.now())
                .page(page)
                .build();
        return node;
    }
    public static Edge edge(Page page, Node source, Node target) {
        int n = EDGE_SEQ.getAndIncrement();
        Edge edge = Edge.builder()
                .page(page)
                .source(source)
                .target(target)
                .type("default")
                .label("edge-label-" + n)
                .sourceHandle("s-" + n)
                .targetHandle("t-" + n)
                .variant("variant-" + n)
                .animated(false)
                .stroke(null)
                .strokeWidth(null)
                .build();
        return edge;
    }
    // 노드 2개를 page에 생성하고 그 사이에 edge까지 한 번에 만드는 편의 메서드
    public static Edge edgeWithNewNodes(Page page) {
        Node s = node(page);
        Node t = node(page);
        return edge(page, s, t);
    }

    public static Note note(User user, Long teamId) {
        int n = NOTE_SEQ.getAndIncrement();
        return Note.builder()
                .user(user)
                .teamId(teamId)
                .subject("note-subject-" + n)
                .content("note-content-" + n)
                .stockSymb("AAPL")
                .noteDate(LocalDate.now())
                .build();
    }
    //Node + Note 연결
    public static Node nodeWithNote(Page page, Note note) {
        Node node = node(page);
        node.attach(note);
        return node;
    }

    public static Directory dir(User user) {
        int n = DIR_SEQ.getAndIncrement();
        return Directory.builder()
                .name("dir-" + n)
                .user(user)
                .parent(null)
                .build();
    }
    public static Directory childDir(User user, Directory parent) {
        int n = DIR_SEQ.getAndIncrement();
        return Directory.builder()
                .name("dir-" + n)
                .user(user)
                .parent(parent)
                .build();
    }
    public static Page page(User user, Directory dir) {
        int n = PAGE_SEQ.getAndIncrement();
        return Page.builder()
                .title("page-" + n)
                .user(user)
                .directory(dir)
                .build();
    }
}
