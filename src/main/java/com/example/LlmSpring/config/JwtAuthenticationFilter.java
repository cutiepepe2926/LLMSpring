package com.example.LlmSpring.config;

import com.example.LlmSpring.util.JWTService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JWTService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. 토큰 검증
            String token = authHeader.substring(7);
            String userId = jwtService.verifyTokenAndUserId(token);

            // 3. 검증 성공 시 SecurityContext에 인증 정보 저장
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // (UserVO 객체를 DB에서 불러와 넣으면 더 좋지만, 일단 ID만으로 인증 처리)
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, // Principal (컨트롤러에서 꺼내 쓸 값)
                        null,
                        Collections.emptyList() // 권한 목록 (필요시 추가)
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // 토큰 검증 실패 시 그냥 통과시킴 -> 뒤쪽 SecurityConfig에서 403 처리됨
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request, response);
    }
}
