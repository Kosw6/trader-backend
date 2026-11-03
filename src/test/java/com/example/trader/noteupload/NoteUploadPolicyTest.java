package com.example.trader.noteupload;

import com.example.trader.entity.Note;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NoteUploadPolicyTest {
    @Test void sharedBrowserRevisionFixture() {
        var note=Note.builder().id(77L).subject("제목").content("가😀\n").noteDate(LocalDate.parse("2026-10-29"))
            .stockSymb("NVDA").stockSymbs(new ArrayList<>(List.of("NVDA","AAPL"))).build();
        assertThat(NoteUploadPolicy.revision(note)).isEqualTo("0967b88b0595802396e164e3b3c6729417cc4eb4d8852e309fccf4de874e1819");
    }
    @Test void byteBoundaryPreservesKoreanAndEmoji() {
        NoteUploadPolicy.assembledText("가".repeat(16384));
        NoteUploadPolicy.assembledText("😀".repeat(12288));
        assertThatThrownBy(()->NoteUploadPolicy.assembledText("가".repeat(16385))).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->NoteUploadPolicy.utf8("\ud800")).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->NoteUploadPolicy.text("x\0y",10)).isInstanceOf(NoteUploadException.class);
    }
    @Test void checksAssembledActiveMarkupIncludingEntityEncodedAttributes() {
        for(String attack:List.of("<scr"+"ipt>alert(1)</script>","<img src=x onerror='alert(1)'>",
            "<svg><a href='javascript:alert(1)'>x</a></svg>","<a href='jav&#x61;script:alert(1)'>x</a>",
            "<iframe srcdoc='x'></iframe>","<div STYLE='color:red'>x</div>"))
            assertThatThrownBy(()->NoteUploadPolicy.assembledText(attack)).isInstanceOf(NoteUploadException.class);
    }
    @Test void benignPlainTextAndLiteralFormattingAreNotRewritten() {
        for(String text:List.of("매출 < 130, 이익 > 20\n근거: https://example.com","  가나다😀\n\t\"원문\"\\  ","<b>강조</b>"))
            NoteUploadPolicy.assembledText(text);
        assertThat(NoteUploadPolicy.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
