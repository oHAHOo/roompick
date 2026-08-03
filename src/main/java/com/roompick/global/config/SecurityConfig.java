package com.roompick.global.config;

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
        "/actuator/metrics",
        "/actuator/prometheus"
    };

    private static final String[] PUBLIC_GET_PATHS = {
        "/api/v1/accommodations/**",
        "/api/v1/rooms/**"
    };

    private static final String ADMIN_PATH_PATTERN = "/api/v1/admin/**";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtTokenProvider jwtTokenProvider,
        TokenBlacklistRepository tokenBlacklistRepository,
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
        JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
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
