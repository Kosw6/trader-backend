package com.example.trader.noteupload;

import com.example.trader.entity.Note;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;

/** The assembled value is plain text, never HTML. No trimming or lossy sanitization. */
public final class NoteUploadPolicy {
    public static final int REQUEST_BYTES=7*1024, CONTENT_BYTES=48*1024, PART_BYTES=4096, MAX_PARTS=64;
    private NoteUploadPolicy() {}

    public static byte[] utf8(String value) {
        if (value==null) throw invalid("내용은 null일 수 없습니다.");
        try {
            ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            byte[] bytes=new byte[b.remaining()];b.get(bytes);return bytes;
        } catch (CharacterCodingException e) { throw invalid("문자 인코딩을 확인해주세요."); }
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(utf8(value))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String revision(Note note) {
        var fields=new ArrayList<String>();
        Collections.addAll(fields,"note-v1",String.valueOf(note.getId()),note.getSubject(),note.getContent(),
            note.getNoteDate()==null?"":note.getNoteDate().toString(),
            note.getTeamId()==null?"":note.getTeamId().toString(),note.getStockSymb(),
            String.valueOf(note.getStockSymbs().size()));
        fields.addAll(note.getStockSymbs());
        try {
            var hash=MessageDigest.getInstance("SHA-256");
            for(String field:fields) {
                byte[] bytes=utf8(field==null?"":field);
                hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static void text(String value, int limit) {
        if(utf8(value).length>limit) throw new NoteUploadException(HttpStatus.PAYLOAD_TOO_LARGE,
            "NOTE_REQUEST_TOO_LARGE","노트 크기 제한을 초과했어요. 입력은 유지됩니다.");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if((c<32 && c!='\n' && c!='\r' && c!='\t') || c==127)
                throw invalid("노트에 지원하지 않는 제어 문자가 있어요.");
        }
    }

    /** Re-assembly is not re-inspected by WAF. Check the whole text before persistence. */
    public static void assembledText(String value) {
        text(value,CONTENT_BYTES);
        var active=Set.of("script","style","iframe","object","embed","link","meta","base",
            "form","input","button","textarea","select","svg","math","frame","frameset");
        var doc=Jsoup.parseBodyFragment(value);
        for(var element:doc.getAllElements()) {
            if(active.contains(element.normalName())) throw unsafe();
            for(var attribute:element.attributes()) {
                String name=attribute.getKey().toLowerCase(Locale.ROOT);
                if(name.startsWith("on") || name.equals("style") || name.equals("srcdoc")) throw unsafe();
                if(Set.of("href","src","action","formaction","xlink:href","background").contains(name)) {
                    String url=attribute.getValue().replaceAll("[\\p{Cntrl}\\s]+", "").toLowerCase(Locale.ROOT);
                    if(url.startsWith("javascript:") || url.startsWith("vbscript:") || url.startsWith("data:")) throw unsafe();
                }
            }
        }
    }

    public static NoteUploadException invalid(String message) {
        return new NoteUploadException(HttpStatus.BAD_REQUEST,"NOTE_UPLOAD_INVALID",message);
    }
    private static NoteUploadException unsafe() {
        return new NoteUploadException(HttpStatus.BAD_REQUEST,"NOTE_CONTENT_UNSAFE",
            "실행 가능한 HTML은 저장할 수 없어요. 일반 텍스트로 작성해주세요. 입력은 유지됩니다.");
    }
}
