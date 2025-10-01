package com.example.trader.security.service;

import com.example.trader.entity.Note;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.UserRepository;
import com.example.trader.security.details.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    public boolean canAccessNote(Authentication authentication, Long noteId) {
        // 현재 사용자 정보 가져오기
        UserContext userDetails = (UserContext) authentication.getPrincipal();
        Long currentUserId = userDetails.getUserDto().getId();

        // 게시물 작성자와 현재 사용자 비교
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_NOTE));
        if (!note.getUser().getId().equals(((UserContext) authentication.getPrincipal()).getUserDto().getId())) {
            throw new AccessDeniedException("해당 노트의 접근 권한이 없습니다."); // 권한 없으
        }
        return note.getUser().getId().equals(currentUserId);
    }

    public boolean canAccessUser(Authentication authentication, Long userId){
        // 현재 사용자 정보 가져오기
        UserContext userDetails = (UserContext) authentication.getPrincipal();
        Long currentUserId= userDetails.getUserDto().getId();

        // 게시물 작성자와 현재 사용자 비교
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NON_EXIST_USER));

        if(!user.getId().equals(((UserContext) authentication.getPrincipal()).getUserDto().getId())){
            throw new AccessDeniedException("해당 유저정보의 접근 권한이 없습니다."); // 권한 없으
        }
        return user.getId().equals(currentUserId);
    }
    //현재 쓰레드 내의 컨텍스트의 유저 아이디를 반환
    public static Long getAuthenticationUserId(){
        UserContext userContext = (UserContext) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userContext.getUserDto().getId();
    }
    public User getAuthenticationUser(){
        UserContext userContext = (UserContext) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findById(userContext.getUserDto().getId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.NON_EXIST_USER));
    }
}
