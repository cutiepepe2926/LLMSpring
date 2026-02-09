package com.example.LlmSpring.config;

import com.example.LlmSpring.user.UserMapper;
import com.example.LlmSpring.user.UserVO;
import com.example.LlmSpring.util.EncryptionUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserMapper userMapper;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final EncryptionUtil encryptionUtil;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. GitHub에서 넘어온 정보 추출
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        // 2. Access Token 추출
        String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();
        String principalName = oauthToken.getName();

        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientService.loadAuthorizedClient(
                clientRegistrationId,
                principalName
        );

        if (authorizedClient == null) {
            throw new ServletException("OAuth2AuthorizedClient not found");
        }

        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        String githubId = String.valueOf(oAuth2User.getAttributes().get("id"));

        // 3. 쿠키에서 'link_user_id' 찾기
        String targetUserId = null;
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("link_user_id".equals(cookie.getName())) {
                    targetUserId = cookie.getValue();

                    // [중요] 사용한 식별 쿠키 즉시 삭제
                    Cookie deleteCookie = new Cookie("link_user_id", null);
                    deleteCookie.setPath("/");
                    deleteCookie.setMaxAge(0);
                    response.addCookie(deleteCookie);
                    break;
                }
            }
        }

        // 4. 연동 로직 수행
        if (targetUserId != null) {
            UserVO user = userMapper.getUserInfo(targetUserId);

            if (user != null) {
                try {
                    // 암호화 및 DB 업데이트
                    String encryptedToken = encryptionUtil.encrypt(accessToken);
                    user.setGithubId(githubId);
                    user.setGithubToken(encryptedToken);

                    userMapper.updateGithubInfo(user);
                    log.info("Github 연동 성공 - UserID: {}", targetUserId);
                } catch (Exception e) {
                    log.error("토큰 암호화 실패", e);
                }
            }
        } else {
            log.warn("Github 연동 실패: link_user_id 쿠키를 찾을 수 없습니다.");
        }

        // ============================================================
        // [핵심 해결책] 세션 및 JSESSIONID 쿠키 완전 제거
        // ============================================================

        // 1. JSESSIONID 쿠키 삭제 (브라우저에게 삭제 명령 전달)
        Cookie sessionCookie = new Cookie("JSESSIONID", null);
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0);
        response.addCookie(sessionCookie);

        // 2. 서버 측 세션 무효화
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // 5. 프론트엔드로 리다이렉트
        response.sendRedirect(frontendUrl + "/myPage");
    }
}