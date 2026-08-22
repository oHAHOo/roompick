package com.roompick.global.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.roompick.domain.member.repository.TokenBlacklistRepository;
import com.roompick.global.security.JwtAccessDeniedHandler;
import com.roompick.global.security.JwtAuthenticationEntryPoint;
import com.roompick.global.security.JwtAuthenticationFilter;
import com.roompick.global.security.JwtProperties;
import com.roompick.global.security.JwtTokenProvider;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
        "/api/v1/auth/signup",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/actuator/health",
        "/actuator/info",
        "/actuator/metrics/**",
        "/actuator/prometheus"
    };

    private static final String[] PUBLIC_GET_PATHS = {
        "/api/v1/accommodations/**",
        "/api/v1/rooms/**",
        "/api/v1/places/**"
    };

    private static final String ADMIN_PATH_PATTERN = "/api/v1/admin/**";

    @Value("${roompick.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        // Idempotency-Key는 예약 생성 API가 요구하는 헤더입니다. 여기에 없으면
        // 브라우저 preflight가 막혀 다른 도메인에서 예약을 만들 수 없습니다.
        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtTokenProvider jwtTokenProvider,
        TokenBlacklistRepository tokenBlacklistRepository,
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
        JwtAccessDeniedHandler jwtAccessDeniedHandler,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                .requestMatchers(ADMIN_PATH_PATTERN).hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistRepository),
                UsernamePasswordAuthenticationFilter.class);

        SecurityFilterChain securityFilterChain = http.build();
        return securityFilterChain;
    }
}
