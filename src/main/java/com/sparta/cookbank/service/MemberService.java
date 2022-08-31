package com.sparta.cookbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.cookbank.ResponseDto;
import com.sparta.cookbank.domain.Member.Member;
import com.sparta.cookbank.domain.Member.dto.GoogleUserInfoDto;
import com.sparta.cookbank.domain.Member.dto.KakaoUserInfoDto;
import com.sparta.cookbank.domain.Member.dto.LoginRequestDto;
import com.sparta.cookbank.domain.Member.dto.SignupRequestDto;
import com.sparta.cookbank.domain.RefreshToken.DTO.TokenDto;
import com.sparta.cookbank.domain.RefreshToken.RefreshToken;
import com.sparta.cookbank.repository.MemberRepository;
import com.sparta.cookbank.repository.RefreshTokenRepository;
import com.sparta.cookbank.security.SecurityUtil;
import com.sparta.cookbank.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import netscape.javascript.JSObject;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final TokenProvider tokenProvider;

    private final PasswordEncoder passwordEncoder;

    private final MemberRepository memberRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final MailService mailService;



    @Value("${kakao.redirect.url}")
    private String KAKAO_REDIRECT_URI;

    @Value("${kakao.client.id}")
    private String KAKAO_CLIENT_ID;

    @Value("${google.client.id}")
    private String GOOGLE_CLIENT_ID;
    @Value("${google.client.pw}")
    private String GOOGLE_CLIENT_SECRET;
    @Value("${google.redirect.url}")
    private String GOOGLE_REDIRECT_URI;

    public Long signup(SignupRequestDto requestDto) {
        if(memberRepository.existsByEmail(requestDto.getEmail())) throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        // 패스워드 인코딩
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        String key = UUID.randomUUID().toString();
        Member member = Member.builder()
                .email(requestDto.getEmail())
                .username(requestDto.getUsername())
                .password(encodedPassword)
                .mail_auth(false)
                .mail_key(key)
                .build();
        mailService.sendSimpleMessage(requestDto,key);
        return memberRepository.save(member).getId();
    }

    public Member login(LoginRequestDto requestDto, HttpServletResponse response) {
        Member member = memberRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (member.getKakaoId() != null) throw new IllegalArgumentException("카카오로 가입된 유저입니다.");
        // 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호를 잘못 입력하셨습니다.");
        }
        TokenDto tokenDto = tokenProvider.generateTokenDto(member);
        response.setHeader("Authorization","Bearer " + tokenDto.getAccessToken());
        response.setHeader("Refresh-Token",tokenDto.getRefreshToken());
        return member;
    }

    public Member reissue(HttpServletRequest request, HttpServletResponse response){
        String accessToken = request.getHeader("Authorization");
        if (StringUtils.hasText(accessToken) && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        } else throw new IllegalArgumentException("엑세스토큰의 타입이 잘못되었습니다.");
        if (!tokenProvider.validateTokenWithoutTime(accessToken)){
            throw new IllegalArgumentException("엑세스토큰이 잘못되었습니다.");
        }
        String refreshToken = request.getHeader("Refresh-Token");
        System.out.println(refreshToken);
        // 서버에 해당 리프레시 토큰이 존재하는지 확인
        RefreshToken refreshTokenObj = refreshTokenRepository.findByTokenValue(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("서버에 존재하지 않는 리프레시 토큰입니다."));
        // Member 객체 가져오기
        Member member = refreshTokenObj.getMember();

        //토큰 생성 및 헤더에 저장
        TokenDto tokenDto = tokenProvider.generateTokenDto(member);
        response.setHeader("Authorization","Bearer " + tokenDto.getAccessToken());
        response.setHeader("Refresh-Token",tokenDto.getRefreshToken());
        return member;
    }

    public void logout() {
        Member member = memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(
                () -> new IllegalArgumentException("해당 유저가 존재하지 않습니다.")
        );
        refreshTokenRepository.deleteByMember(member);
    }

    public Member test(){
        return memberRepository.findById(SecurityUtil.getCurrentMemberId()).orElseThrow(
                () -> new IllegalArgumentException("해당 유저가 존재하지 않습니다.")
        );
    }

    public Member kakaoLogin(String code, HttpServletResponse response) throws JsonProcessingException {
        System.out.println(code);
        // 1. "인가 코드"로 "액세스 토큰" 요청
        String accessToken = getKakaoAccessToken(code);
        // 2. 토큰으로 카카오 API 호출
        KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(accessToken);

        // DB 에 중복된 Kakao Id 가 있는지 확인
        String kakaoId = kakaoUserInfo.getId().toString();
        Member kakaoUser = memberRepository.findByKakaoId(kakaoId)
                .orElse(null);
        if (kakaoUser == null) {
            // 회원가입
            if(memberRepository.existsByEmail(kakaoUserInfo.getEmail()))
                throw new IllegalArgumentException("이미 가입된 이메일입니다.");
            Member member = Member.builder()
                    .email(kakaoUserInfo.getEmail())
                    .username(kakaoUserInfo.getNickname())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .kakaoId(kakaoId)
                    .mail_auth(true)
                    .build();
            memberRepository.save(member);
            kakaoUser = member;
        }
        TokenDto tokenDto = tokenProvider.generateTokenDto(kakaoUser);
        response.setHeader("Authorization","Bearer " + tokenDto.getAccessToken());
        response.setHeader("Refresh-Token",tokenDto.getRefreshToken());
        return kakaoUser;
    }

    private String getKakaoAccessToken(String code) throws JsonProcessingException {
        // 카카오에 보낼 api
        WebClient client = WebClient.builder()
                .baseUrl("https://kauth.kakao.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // 카카오 서버에 요청 보내기 & 응답 받기
        JsonNode response = client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", KAKAO_CLIENT_ID)
                        .queryParam("redirect_uri", KAKAO_REDIRECT_URI)
                        .queryParam("code", code)
                        .build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return response.get("access_token").asText();
    }

    private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
        // 카카오에 보낼 api
        WebClient client = WebClient.builder()
                .baseUrl("https://kapi.kakao.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // 카카오 서버에 요청 보내기 & 응답 받기
        JsonNode response = client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/user/me")
                        .build())
                .header("Authorization","Bearer " + accessToken)
                .retrieve().bodyToMono(JsonNode.class).block();
        Long id = response.get("id").asLong();
        String nickname = response.get("properties")
                .get("nickname").asText();
        String email = response.get("kakao_account")
                .get("email").asText();
        return new KakaoUserInfoDto(id, nickname, email);
    }

    public Member googleLogin(String code, HttpServletResponse response) throws JsonProcessingException {
        // 1. "인가 코드"로 "액세스 토큰" 요청
        String accessToken = getGoogleAccessToken(code);
        // 2. 토큰으로 카카오 API 호출
        GoogleUserInfoDto googleUserInfo = getGoogleUserInfo(accessToken);

        // DB 에 중복된 Kakao Id 가 있는지 확인
        String googleId = googleUserInfo.getId();
        Member googleUser = memberRepository.findByGoogleId(googleId)
                .orElse(null);
        if(googleUser == null){
            // 회원가입
            Member member = Member.builder()
                    .email(googleUserInfo.getEmail())
                    .username(googleUserInfo.getName())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .googleId(googleId)
                    .mail_auth(true)
                    .build();
            memberRepository.save(member);
            googleUser = member;
        }
        TokenDto tokenDto = tokenProvider.generateTokenDto(googleUser);
        response.setHeader("Authorization","Bearer " + tokenDto.getAccessToken());
        response.setHeader("Refresh-Token",tokenDto.getRefreshToken());
        return googleUser;
    }

    private String getGoogleAccessToken(String code) throws JsonProcessingException {
        // 구글에 보낼 api
        WebClient client = WebClient.builder()
                .baseUrl("https://oauth2.googleapis.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // 구글 서버에 요청 보내기 & 응답 받기
        JsonNode response = client.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/token")
                        .queryParam("code", code)
                        .queryParam("client_id", GOOGLE_CLIENT_ID)
                        .queryParam("client_secret", GOOGLE_CLIENT_SECRET)
                        .queryParam("redirect_uri", GOOGLE_REDIRECT_URI)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return response.get("access_token").asText();
    }

    private GoogleUserInfoDto getGoogleUserInfo(String accessToken) throws JsonProcessingException {
        // 구글에 보낼 api
        WebClient client = WebClient.builder()
                .baseUrl("https://www.googleapis.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // 구글 서버에 요청 보내기 & 응답 받기
        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth2/v1/userinfo")
                        .build())
                .header("Authorization","Bearer " + accessToken)
                .retrieve().bodyToMono(JsonNode.class).block();

        String id = response.get("id").toString();
        String name = response.get("name").toString();
        String email = response.get("email").toString();
        return new GoogleUserInfoDto(id,name,email);
    }

    @Transactional
    public String emailCheck(String memberEmail, String key) {
        Member member = memberRepository.findByEmail(memberEmail).orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 이메일입니다.")
        );
        if(member.getMail_key().equals(key)) {
            if (member.isMail_auth()) {
                return "already";
            }
            member.EmailCheck();
            return "success";
        }
        return "fail";
    }
}
