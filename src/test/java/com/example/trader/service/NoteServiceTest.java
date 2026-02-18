
//package com.example.trader.service;
//
//import com.example.trader.entity.Note;
//import com.example.trader.entity.Role;
//import com.example.trader.entity.User;
//import org.assertj.core.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.annotation.Rollback;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.*;
//
//@Transactional
//@SpringBootTest
//public class NoteServiceTest {
//    @Autowired
//    private NoteService noteService;
//    @Autowired
//    private UserService userService;
//
//    private Note testNote1;
//    private Note testNote2;
//    private Note testNote3;
//
//
//    private User testUser;
////    @BeforeEach
////    void setUp() {
////        testUser = User.builder()
////                .email("test@example.com")
////                .username("Test User2")
////                .loginId("test2")
////                .password("test2")
////                .nick_name("test_user2")
////                .role(Role.USER)
////                .build();
////
////        testNote1 = Note.builder()
////                .content("1")
////                .subject("1")
////                .user(testUser)
////                .stockSymb("TSLA")
////                .build();
////        testNote2 = Note.builder()
////                .content("2")
////                .subject("2")
////                .user(testUser)
////                .stockSymb("TSLA")
////                .build();
////        testNote3 = Note.builder()
////                .content("3")
////                .subject("3")
////                .user(testUser)
////                .stockSymb("AA")
////                .build();
////        testUser.addNotes(List.of(testNote1,testNote2,testNote3));
////        userService.createUser(testUser);
////        noteService.saveNote(testNote1);
////        noteService.saveNote(testNote2);
////        noteService.saveNote(testNote2);
////    }
//
//    @Test
//    void findNotesByNoteId(){
//        Note noteById = noteService.findNoteById(1L);
//        assertThat(noteById.getId()).isEqualTo(1L);
//    }
//    @Test
//    void findAllNotes(){
//        Long userId = userService.findUserByLoginId("test2").getId();
//        List<Note> notes = noteService.findAllNoteByUserIdAndStock(userId,"TSLA");
//        for (Note note : notes) {
//            System.out.println(note.getUserId());
//        }
//        Note note1 = noteService.findNoteById(1L);
//        Note note2 = noteService.findNoteById(2L);
//        assertThat(notes).size().isEqualTo(2);
//    }
//
//    @Test
//    void deleteNote(){
//        noteService.deleteNote(1L);
//        assertThatThrownBy(()->{noteService.findNoteById(1L);}).isInstanceOf(RuntimeException.class);
//    }
//
//    @Test
//    @Rollback(value = false)
//    void updateNote(){
//        Note noteById = noteService.findNoteById(2L); // 엔티티 조회 및 영속 상태 유지
//        noteById.changeContent("change");           // 변경 감지로 자동 업데이트
//
//        Note changeNote = noteService.findNoteById(2L); // 동일한 트랜잭션 내에서 조회
//        assertThat(changeNote.getContent()).isEqualTo("change"); // 검증
//    }
//}
