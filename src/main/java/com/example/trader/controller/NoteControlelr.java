package com.example.trader.controller;

import com.example.trader.dto.NoteDto;
import com.example.trader.dto.RequestNoteDto;
import com.example.trader.entity.Note;
import com.example.trader.entity.User;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.service.SecurityService;
import com.example.trader.service.NoteService;
import com.example.trader.service.TeamService;
import com.example.trader.service.UserService;
import com.nimbusds.jose.proc.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "NOTE API", description = "학습노트 관련 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/note")
public class NoteControlelr {
    private final NoteService noteService;
    private final UserService userService;
    private final TeamService teamService;

    private Boolean validateTeam(RequestNoteDto noteDto){
        if (noteDto.teamId()==null){
            return false;
        }else{
            return true;
        }
    }
    @Operation(
            summary = "모든 노트 조회"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping
    public ResponseEntity findAllNote(@ParameterObject
                                          @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = SecurityService.getAuthenticationUserId();
        Page page = noteService.findAllNote(userId, pageable);
        if(page.getContent().isEmpty()){
            return ResponseEntity.status(BaseResponseStatus.INVALID_NOTE.getCode()).body(String.format("노트가 없습니다."));
        }else{
            return ResponseEntity.ok(page);
        }
    }
    @Operation(
            summary = "해당 주식의 모든 노트 조회"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping(params = {"stockName"})
    public ResponseEntity findAllNoteByStock(@Parameter(name = "stockName", example = "TSLA") @RequestParam String stockName,     // stockName 파라미터
                                              @ParameterObject
                                              @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable){
        Long userId = SecurityService.getAuthenticationUserId();
        Page page = noteService.findAllNoteByUserIdAndStock(userId, stockName, pageable);
        if(page.getContent().isEmpty()){
            return ResponseEntity.status(BaseResponseStatus.INVALID_NOTE.getCode()).body(String.format("주식명이 %s인 노트가 없습니다.",stockName));
        }else{
            return ResponseEntity.ok(page);
        }

    }
    @GetMapping("/{noteId}")
    @PreAuthorize("@securityService.canAccessNote(authentication, #noteId)") //자신의 노트가 아닌 타 노트에 접근하는 것을 방지
    public ResponseEntity findNoteByNoteId(@PathVariable Long noteId){
        Note note = noteService.findNoteById(noteId);
        NoteDto noteDto = NoteDto.builder().
                id(noteId).
                teamId(note.getTeamId()).
                userId(note.getUser().getId()).
                content(note.getContent()).
                stockSymb(note.getStockSymb()).
                subject(note.getSubject()).build();
        return ResponseEntity.ok(noteDto);
    }

    @Operation(
            summary = "노트 저장"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @PostMapping("/save")
    public ResponseEntity saveNote(@RequestBody RequestNoteDto noteDto){
        UserContext userContext = (UserContext) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long id = userContext.getUserDto().getId();
        User user = userService.findUserByUserId(id);
        Note note;
        if(validateTeam(noteDto)){
            note = Note.builder().
                    stockSymb(noteDto.stockSymb()).
                    user(user).
                    teamId(noteDto.teamId()).
                    content(noteDto.content()).
                    subject(noteDto.subject()).build();
        }else{
            note = Note.builder().
                    stockSymb(noteDto.stockSymb()).
                    user(user).
                    teamId(null).
                    content(noteDto.content()).
                    subject(noteDto.subject()).build();
        }
        Long noteId = noteService.saveNote(note);
        return ResponseEntity.ok(noteId);
    }
    @Operation(
            summary = "노트 삭제"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @DeleteMapping("/delete/{noteId}")
    @PreAuthorize("@securityService.canAccessNote(authentication, #noteId)")
    public ResponseEntity deleteNoteById(@Parameter(name = "noteId")@PathVariable("noteId") Long noteId){
        noteService.deleteNote(noteId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    @Operation(
            summary = "노트 수정"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @PutMapping("/update/{noteId}")
    @PreAuthorize("@securityService.canAccessNote(authentication, #noteId)")
    public ResponseEntity updateNote(@RequestBody RequestNoteDto noteDto, @PathVariable Long noteId){
        Note note = noteService.findNoteById(noteId);
        if(validateTeam(noteDto)){
            note.changeContent(noteDto.content());
            note.changestockSymb(noteDto.stockSymb());
            note.changeSubject(noteDto.subject());
        }else{
            //TODO:
//            note = Note.builder().
//                    id(noteId).
//                    stockSymb(noteDto.stockSymb()).
//                    userId(noteDto.userId()).
//                    teamId(null).
//                    content(noteDto.content()).
//                    subject(noteDto.subject()).build();
        }
        Long id = noteService.saveNote(note);
        return ResponseEntity.ok(id);
    }



}
