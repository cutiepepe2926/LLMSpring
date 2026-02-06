package com.example.LlmSpring.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // 추가: final 필드 자동 주입
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter; // 1. 필터 주입 받기

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowedOrigins(java.util.List.of("http://localhost:3000"));
                    config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                    config.setAllowedHeaders(java.util.List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 2. 구체적인 권한 설정 (순서 중요!)
                        .requestMatchers("/api/auth/login", "/api/auth/signup", "/api/auth/reissue").permitAll() // 로그인, 회원가입은 누구나
                        .requestMatchers("/api/auth/validate").authenticated() // [핵심] 이 경로는 토큰이 있어야만 통과
                        .requestMatchers("/api/**").permitAll() // (개발 중 편의를 위해 나머지 api는 열어둘 경우)
                        .anyRequest().authenticated()
                )
                // 3. 필터 체인에 등록 (UsernamePasswordAuthenticationFilter 앞에 실행)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler("http://localhost:3000/myPage"))
                );

        return http.build();
    }
}