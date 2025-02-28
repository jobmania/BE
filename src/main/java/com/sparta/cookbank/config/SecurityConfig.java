package com.sparta.cookbank.config;

import com.sparta.cookbank.security.JwtAccessDeniedHandler;
import com.sparta.cookbank.security.JwtAuthenticationEntryPoint;
import com.sparta.cookbank.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.List;

@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final TokenProvider tokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    // h2 database 테스트가 원활하도록 관련 API 들은 전부 무시
    @Override
    public void configure(WebSecurity web) {
        web.ignoring()
                .antMatchers("/h2-console/**", "/favicon.ico");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors();
        // CSRF 설정 Disable
        http.csrf().disable()

                // exception handling 할 때 우리가 만든 클래스를 추가
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)

                // h2-console 을 위한 설정을 추가
                .and()
                .headers()
                .frameOptions()
                .sameOrigin()

                // 시큐리티는 기본적으로 세션을 사용
                // 여기서는 세션을 사용하지 않기 때문에 세션 설정을 Stateless 로 설정
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                // 로그인, 회원가입 API 는 토큰이 없는 상태에서 요청이 들어오기 때문에 permitAll 설정
                .and()
                .authorizeRequests()
                .antMatchers("/api/user/signup").permitAll()
                .antMatchers("/api/user/signin").permitAll()
                .antMatchers("/api/password").permitAll()
                .antMatchers("/api/reissue").permitAll()
                .antMatchers("/api/recipe/fix/**").permitAll()
                .antMatchers("/api/user/email/**").permitAll()
                .antMatchers("/user/kakao/callback/**").permitAll()
                .antMatchers("/user/google/callback/**").permitAll()
                .antMatchers("/api/ingredients/autocomplete").permitAll()
                .antMatchers("/api/ingredients/search").permitAll()
                .antMatchers("/v2/**","/v2/api-docs", "/swagger-resources/**",
                        "/swagger-ui.html/**","/swagger-ui/**").permitAll()
                .antMatchers("/webjars/**").permitAll()
                .antMatchers(HttpMethod.GET,"/api/class/**").permitAll()
                .antMatchers(HttpMethod.GET,"/api/chat/**").permitAll()
                //stomp
                .antMatchers("/sub/**").permitAll()
                .antMatchers("/pub/**").permitAll()
                .antMatchers("/ws/**").permitAll()
                .antMatchers("/stomp/**").permitAll()
                .antMatchers("/websocket/**").permitAll()
                // 나중에 지우기
                .antMatchers("/api/recipes/recommend").permitAll()
                .antMatchers("/api/recipe/{id}").permitAll()
                .antMatchers("/api/recipes").permitAll()
                .antMatchers("/api/recipes/search").permitAll()
                .antMatchers("/chat/**").permitAll()
                .antMatchers("/api/mapping").permitAll()
                .anyRequest().authenticated()   // 나머지 API 는 전부 인증 필요

                // JwtFilter 를 addFilterBefore 로 등록했던 JwtSecurityConfig 클래스를 적용
                .and()
                .apply(new JwtSecurityConfig(tokenProvider));
    }
}