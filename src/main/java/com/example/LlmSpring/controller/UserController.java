package com.example.LlmSpring.controller;

import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.user.UserService;
import com.example.LlmSpring.user.response.UserSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins="*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 멤버 초대 후보군 검색 API
     * GET /api/user/search?keyword=...
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @AuthenticationPrincipal String userId,
            @RequestParam("keyword") String keyword) {

        try {
            // 3. 필수 파라미터 검증
            if (keyword == null || keyword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("검색어를 입력해주세요.");
            }

            // 4. 서비스 호출 및 결과 반환 (주입받은 userId 사용)
            List<UserSearchResponseDTO> results = userService.searchUsersForInvitation(keyword, userId);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("유저 검색 중 서버 오류가 발생했습니다.");
        }
    }

    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(@AuthenticationPrincipal String userId) {
        UserVO userVO = userService.getUserInfo(userId);

        if (userVO != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userVO.getUserId());
            response.put("name", userVO.getName());
            response.put("email", userVO.getEmail());
            response.put("filePath", userVO.getFilePath());

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(404).body("User not found");
        }
    }

    @GetMapping("/fullInfo")
    public ResponseEntity<?> getUserFullInfo(@AuthenticationPrincipal String userId) {
        System.out.println("사용자 모든 정보 받기 위해 진입");

        UserVO userVO = userService.getUserInfo(userId);

        if (userVO != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("name", userVO.getName());
            response.put("userId", userVO.getUserId());
            response.put("email", userVO.getEmail());
            response.put("regDate",  userVO.getRegDate());
            response.put("githubId", userVO.getGithubId());
            response.put("filePath", userVO.getFilePath());

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(404).body("User not found");
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestPart(value="file", required=false) MultipartFile file,
            @RequestPart(value = "nickname") String nickname
    ){
        System.out.println("프로필 수정 맵핑");
        try{
            userService.updateProfile(userId, nickname, file);
            return ResponseEntity.ok(Collections.singletonMap("message", "프로필이 수정되었습니다"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("프로필 수정 실패: " + e.getMessage());
        }
    }
}