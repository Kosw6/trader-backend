package com.example.trader.controller;

import com.example.trader.dto.RequestUserDto;
import com.example.trader.dto.ResponseUserDto;
import com.example.trader.entity.User;
import com.example.trader.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "USER API", description = "유저관련 관련 API")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserContoller {
    private final UserService userService;

    @Operation(
            summary = "유저조회"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @PreAuthorize("@securityService.canAccessUser(authentication, #id)")
    @GetMapping("/{id}")
    public ResponseEntity findUserById(@Parameter(name = "id",description = "유저ID")@PathVariable Long id){
        ResponseUserDto responseUserDto = ResponseUserDto.of(userService.findUserByUserId(id));
        return ResponseEntity.status(HttpStatus.OK).body(responseUserDto);
    }

    @Operation(
            summary = "유저 정보 수정"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @PutMapping("/update/{id}")
    @PreAuthorize("@securityService.canAccessUser(authentication, #id)")
    //TODO:값이 덜 들어간 데이터에 대해서 처리하기
    public ResponseEntity updateUser(@PathVariable Long id, @RequestBody RequestUserDto userDto){
        User user = User.of(userDto);
        Long userId = userService.updateUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }

    @Operation(
            summary = "유저 삭제"
    )
    @ApiResponse(responseCode = "200", description = "성공")
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("@securityService.canAccessUser(authentication, #id)")
    public ResponseEntity deleteUser(@Parameter(name = "id",description = "유저ID")@PathVariable Long id){
        userService.deleteUser(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
