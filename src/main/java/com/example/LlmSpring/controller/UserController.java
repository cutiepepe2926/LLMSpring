package com.example.LlmSpring.controller;

import com.example.LlmSpring.user.UserService;
import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 멤버 초대 후보군 검색 API
     * GET /api/users/search?keyword=...&myUserId=...
     * * @param keyword 검색 키워드 (ID 또는 이름)
     * @param myUserId 요청자 본인의 ID (결과 제외용)
     * @return 검색된 유저 정보 리스트
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponseDTO>> searchUsers(
            @RequestParam("keyword") String keyword,
            @RequestParam("myUserId") String myUserId) {

        List<UserSearchResponseDTO> results = userService.searchUsersForInvitation(keyword, myUserId);
        return ResponseEntity.ok(results);
    }
}
