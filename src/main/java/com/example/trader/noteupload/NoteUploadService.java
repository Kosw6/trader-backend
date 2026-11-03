package com.example.trader.noteupload;

import com.example.trader.dto.RequestNoteDto;
import com.example.trader.entity.Note;
import com.example.trader.entity.NoteTickers;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.UserRepository;
import com.example.trader.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NoteUploadService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final NoteService notes;
    private final NoteRepository noteRepository;
    private final UserRepository users;

    public record Start(UUID requestId, Long noteId, String baseRevision, String subject, LocalDate noteDate,
                        Long teamId, String stockSymb, List<String> stockSymbs,
                        int contentBytes, int partCount, String contentSha256) {}
    public record State(UUID uploadId, String status, List<Integer> receivedParts, Instant expiresAt, Long noteId) {}
    private record Upload(UUID id,Long target,String revision,String metadata,int bytes,int count,
                          String hash,String status,Long result,Instant expires,boolean live) {}

    private void owner(long owner) {
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,owner).isEmpty())
            throw new NoteUploadException(HttpStatus.UNAUTHORIZED,"NOTE_UPLOAD_AUTH_REQUIRED","다시 로그인해주세요.");
    }
    private Upload find(long owner,UUID id,boolean lock) {
        var rows=jdbc.query("""
            SELECT *, CASE WHEN status='OPEN' THEN expires_at>clock_timestamp()
                           ELSE receipt_expires_at>clock_timestamp() END AS live
            FROM note_upload WHERE owner_id=? AND id=?
            """+(lock?" FOR UPDATE":""),(rs,n)->new Upload((UUID)rs.getObject("id"),
                rs.getObject("target_note_id",Long.class),rs.getString("base_revision"),rs.getString("metadata"),
                rs.getInt("content_bytes"),rs.getInt("part_count"),rs.getString("content_sha256"),
                rs.getString("status"),rs.getObject("result_note_id",Long.class),rs.getTimestamp("expires_at").toInstant(),rs.getBoolean("live")),owner,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Upload require(long owner,UUID id,boolean lock) {
        Upload u=find(owner,id,lock);
        if(u==null) throw new NoteUploadException(HttpStatus.NOT_FOUND,"NOTE_UPLOAD_NOT_FOUND","전송 정보를 찾을 수 없어요.");
        if(!u.live()) throw new NoteUploadException(HttpStatus.GONE,"NOTE_UPLOAD_EXPIRED","전송 시간이 지났어요. 입력은 유지됩니다.");
        return u;
    }
    private void open(Upload u) {
        if(!u.status().equals("OPEN")) throw conflict("NOTE_UPLOAD_CLOSED","이미 끝난 전송이에요. 저장 결과를 확인해주세요.");
    }
    private State state(Upload u) {
        return new State(u.id(),u.status(),jdbc.queryForList(
            "SELECT part_no FROM note_upload_part WHERE upload_id=? ORDER BY part_no",Integer.class,u.id()),u.expires(),u.result());
    }
    private RequestNoteDto metadata(Start s) {
        return new RequestNoteDto(s.subject(),"",s.stockSymb(),s.noteDate(),s.teamId(),null,null,s.stockSymbs());
    }
    private String json(Object value) {
        try{return mapper.writeValueAsString(value);}
        catch(Exception e){throw NoteUploadPolicy.invalid("노트 입력 형식을 확인해주세요.");}
    }
    private RequestNoteDto metadata(Upload u) {
        try{return mapper.readValue(u.metadata(),RequestNoteDto.class);}
        catch(Exception e){throw new IllegalStateException("NOTE_UPLOAD_METADATA_INVALID");}
    }
    private boolean sameMetadata(String a,String b) {
        try{return mapper.readTree(a).equals(mapper.readTree(b));}
        catch(Exception e){throw new IllegalStateException("NOTE_UPLOAD_METADATA_INVALID");}
    }
    private void validate(Start s) {
        if(s==null || s.requestId()==null || s.contentBytes()<0 || s.contentBytes()>NoteUploadPolicy.CONTENT_BYTES
            || s.partCount()<1 || s.partCount()>NoteUploadPolicy.MAX_PARTS
            || s.contentBytes()>(long)s.partCount()*NoteUploadPolicy.PART_BYTES
            || s.contentSha256()==null || !s.contentSha256().matches("[0-9a-f]{64}")
            || (s.noteId()!=null && (s.noteId()<=0 || s.baseRevision()==null || !s.baseRevision().matches("[0-9a-f]{64}")))
            || (s.noteId()==null && s.baseRevision()!=null))
            throw NoteUploadPolicy.invalid("노트 전송 정보를 확인해주세요.");
        var dto=metadata(s);
        if(!validator.validate(dto).isEmpty() || dto.subject().codePointCount(0,dto.subject().length())>255)
            throw NoteUploadPolicy.invalid("제목·날짜·종목을 확인해주세요.");
        NoteUploadPolicy.text(dto.subject(),1020);
        NoteUploadPolicy.assembledText(dto.subject());
        if(dto.stockSymb()!=null) NoteUploadPolicy.text(dto.stockSymb(),256);
        if(dto.stockSymbs()!=null) for(String symbol:dto.stockSymbs()) NoteUploadPolicy.text(symbol,256);
        try {
            if(dto.stockSymbs()!=null) NoteTickers.normalize(dto.stockSymbs());
            else if(dto.stockSymb()!=null && !dto.stockSymb().isBlank()) NoteTickers.normalize(List.of(dto.stockSymb()));
        } catch(IllegalArgumentException e) { throw NoteUploadPolicy.invalid("종목 정보를 확인해주세요."); }
        if(s.teamId()!=null && s.teamId()<=0) throw NoteUploadPolicy.invalid("그룹 정보를 확인해주세요.");
        if(NoteUploadPolicy.utf8(json(dto)).length>6*1024) throw NoteUploadPolicy.invalid("노트 부가 정보가 너무 커요.");
    }
    private Note existing(long owner,long id,boolean lock) {
        if(lock && jdbc.queryForList("SELECT id FROM note WHERE id=? AND user_id=? FOR UPDATE",Long.class,id,owner).isEmpty())
            throw new NoteUploadException(HttpStatus.NOT_FOUND,"NOTE_UPLOAD_TARGET_NOT_FOUND","수정할 노트를 찾을 수 없어요.");
        return noteRepository.findByIdAndUserId(id,owner).orElseThrow(()->
            new NoteUploadException(HttpStatus.NOT_FOUND,"NOTE_UPLOAD_TARGET_NOT_FOUND","수정할 노트를 찾을 수 없어요."));
    }
    private void team(long owner,Long id) {
        if(id!=null && jdbc.queryForList("""
            SELECT ut.id FROM user_team ut JOIN team t ON t.id=ut.team_id
            WHERE ut.user_id=? AND ut.team_id=? FOR SHARE OF ut,t
            """,Long.class,owner,id).isEmpty())
            throw new NoteUploadException(HttpStatus.FORBIDDEN,"NOTE_UPLOAD_TEAM_FORBIDDEN","그룹 접근 권한을 확인해주세요.");
    }
    private void revision(Note note,String expected) {
        if(!NoteUploadPolicy.revision(note).equals(expected))
            throw conflict("NOTE_UPLOAD_CONFLICT","다른 곳에서 노트가 수정됐어요. 입력을 보관하고 최신 내용을 확인해주세요.");
    }

    @Transactional
    public State start(long owner,Start s) {
        validate(s);owner(owner);
        Upload prior=find(owner,s.requestId(),true);String meta=json(metadata(s));
        if(prior!=null) {
            if(!Objects.equals(prior.target(),s.noteId()) || !Objects.equals(prior.revision(),s.baseRevision())
                || !sameMetadata(prior.metadata(),meta) || prior.bytes()!=s.contentBytes() || prior.count()!=s.partCount()
                || !prior.hash().equals(s.contentSha256()))
                throw conflict("NOTE_UPLOAD_IDEMPOTENCY_CONFLICT","같은 전송 번호의 내용이 달라요. 입력을 확인해주세요.");
            return state(require(owner,s.requestId(),true));
        }
        // Global admission lock only for starting sessions, never for the body transfer.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(648,1)");
        if(jdbc.queryForObject("SELECT count(*) FROM note_upload WHERE owner_id=? AND created_at>clock_timestamp()-interval '1 hour'",Long.class,owner)>=60
            || jdbc.queryForObject("SELECT count(*) FROM note_upload WHERE owner_id=? AND status='OPEN' AND expires_at>clock_timestamp()",Long.class,owner)>=3
            || jdbc.queryForObject("SELECT count(*) FROM note_upload WHERE status='OPEN' AND expires_at>clock_timestamp()",Long.class)>=200)
            throw new NoteUploadException(HttpStatus.TOO_MANY_REQUESTS,"NOTE_UPLOAD_CAPACITY","진행 중인 저장이 많아요. 잠시 후 다시 시도해주세요.");
        if(s.noteId()!=null) {
            Note note=existing(owner,s.noteId(),true);revision(note,s.baseRevision());team(owner,note.getTeamId());
        } else team(owner,s.teamId());
        int inserted=jdbc.update("""
            INSERT INTO note_upload(id,owner_id,target_note_id,base_revision,metadata,content_bytes,part_count,content_sha256)
            VALUES(?,?,?,?,?::jsonb,?,?,?) ON CONFLICT(id) DO NOTHING
            """,s.requestId(),owner,s.noteId(),s.baseRevision(),meta,s.contentBytes(),s.partCount(),s.contentSha256());
        if(inserted!=1) throw new NoteUploadException(HttpStatus.NOT_FOUND,"NOTE_UPLOAD_NOT_FOUND","전송 정보를 찾을 수 없어요.");
        return state(require(owner,s.requestId(),true));
    }

    @Transactional(readOnly=true)
    public State status(long owner,UUID id) { return state(require(owner,id,false)); }

    @Transactional
    public State part(long owner,UUID id,int number,String content) {
        NoteUploadPolicy.text(content,NoteUploadPolicy.PART_BYTES);owner(owner);
        Upload u=require(owner,id,true);open(u);
        if(number<0 || number>=u.count()) throw NoteUploadPolicy.invalid("전송 조각 번호를 확인해주세요.");
        var old=jdbc.queryForList("SELECT content FROM note_upload_part WHERE upload_id=? AND part_no=?",String.class,id,number);
        if(!old.isEmpty()) {
            if(!old.get(0).equals(content)) throw conflict("NOTE_UPLOAD_PART_CONFLICT","이미 받은 전송 조각의 내용이 달라요.");
            return state(u);
        }
        long used=jdbc.queryForObject("SELECT coalesce(sum(octet_length(content)),0) FROM note_upload_part WHERE upload_id=?",Long.class,id);
        if(used+NoteUploadPolicy.utf8(content).length>u.bytes()) throw NoteUploadPolicy.invalid("노트 전송 크기가 일치하지 않아요.");
        jdbc.update("INSERT INTO note_upload_part(upload_id,part_no,content) VALUES(?,?,?)",id,number,content);
        return state(u);
    }

    @Transactional
    public Long complete(long owner,UUID id) {
        owner(owner);Upload u=require(owner,id,true);
        if(u.status().equals("COMPLETED")) return u.result();
        open(u);
        var parts=jdbc.query("SELECT part_no,content FROM note_upload_part WHERE upload_id=? ORDER BY part_no",
            (rs,n)->Map.entry(rs.getInt("part_no"),rs.getString("content")),id);
        if(parts.size()!=u.count()) throw conflict("NOTE_UPLOAD_INCOMPLETE","아직 받지 못한 조각이 있어요. 입력은 유지됩니다.");
        var text=new StringBuilder();
        for(int i=0;i<parts.size();i++) {
            if(parts.get(i).getKey()!=i) throw conflict("NOTE_UPLOAD_INCOMPLETE","아직 받지 못한 조각이 있어요.");
            text.append(parts.get(i).getValue());
        }
        String body=text.toString();
        if(NoteUploadPolicy.utf8(body).length!=u.bytes() || !NoteUploadPolicy.sha256(body).equals(u.hash()))
            throw conflict("NOTE_UPLOAD_HASH_MISMATCH","전송한 내용이 일치하지 않아요. 입력은 유지됩니다.");
        NoteUploadPolicy.assembledText(body);
        RequestNoteDto meta=metadata(u);Note note;
        var logical=mapper.valueToTree(meta);
        ((com.fasterxml.jackson.databind.node.ObjectNode)logical).put("content",body);
        if(NoteUploadPolicy.utf8(json(logical)).length>64*1024)
            throw new NoteUploadException(HttpStatus.PAYLOAD_TOO_LARGE,"NOTE_REQUEST_TOO_LARGE","노트 크기 제한을 초과했어요. 입력은 유지됩니다.");
        if(u.target()==null) {
            team(owner,meta.teamId());
            note=Note.builder().user(users.getReferenceById(owner)).teamId(meta.teamId())
                .subject(meta.subject()).content(body).noteDate(meta.noteDate()).build();
        } else {
            note=existing(owner,u.target(),true);revision(note,u.revision());team(owner,note.getTeamId());
            note.changeSubject(meta.subject());note.changeContent(body);
            note.changeNoteDate(meta.noteDate());
        }
        try { note.applyTickerRequest(meta.stockSymbs(),meta.stockSymb()); }
        catch(IllegalArgumentException e) { throw NoteUploadPolicy.invalid("종목 정보를 확인해주세요."); }
        Long noteId=notes.saveNote(note); // Same service and one transaction: note + receipt + parts cleanup.
        jdbc.update("UPDATE note_upload SET status='COMPLETED',result_note_id=? WHERE id=?",noteId,id);
        jdbc.update("DELETE FROM note_upload_part WHERE upload_id=?",id);
        return noteId;
    }

    @Transactional
    public void cancel(long owner,UUID id) {
        owner(owner);Upload u=require(owner,id,true);
        if(u.status().equals("COMPLETED")) throw conflict("NOTE_UPLOAD_ALREADY_SAVED","이미 저장됐어요. 노트 목록을 확인해주세요.");
        jdbc.update("DELETE FROM note_upload_part WHERE upload_id=?",id);
        jdbc.update("UPDATE note_upload SET status='CANCELLED' WHERE id=?",id);
    }

    @Scheduled(initialDelay=60000,fixedDelay=60000)
    @Transactional
    public void cleanup() {
        var expired=jdbc.queryForList("SELECT id FROM note_upload WHERE status='OPEN' AND expires_at<=clock_timestamp() ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT 500",UUID.class);
        for(UUID id:expired) {
            jdbc.update("DELETE FROM note_upload_part WHERE upload_id=?",id);
            jdbc.update("UPDATE note_upload SET status='CANCELLED' WHERE id=?",id);
        }
        jdbc.update("""
            DELETE FROM note_upload WHERE id IN (SELECT id FROM note_upload WHERE receipt_expires_at<=clock_timestamp()
                ORDER BY receipt_expires_at FOR UPDATE SKIP LOCKED LIMIT 500)
            """);
    }

    private static NoteUploadException conflict(String code,String message) {
        return new NoteUploadException(HttpStatus.CONFLICT,code,message);
    }
}
