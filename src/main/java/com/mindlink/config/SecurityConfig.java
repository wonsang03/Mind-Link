package com.mindlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증은 HttpSession + 컨트롤러 수동 ADMIN 체크로 처리한다.
 * Spring Security 필터는 의도적으로 permitAll — 이 동작을 변경하지 말 것.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()));
		return http.build();
	}
}
