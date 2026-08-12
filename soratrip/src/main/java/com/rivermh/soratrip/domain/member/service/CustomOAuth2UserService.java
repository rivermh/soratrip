package com.rivermh.soratrip.domain.member.service;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 서비스 구분을 위한 ID (google, kakao 등)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 구글에서 전달받은 정보
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String providerId = (String) attributes.get("sub");

        // DB에 저장 또는 업데이트
        Member member = saveOrUpdate(email, name, picture, registrationId, providerId);

        // 정지된 계정은 소셜 로그인도 막는다 (폼 로그인 쪽 DisabledException과 동일한 의도).
        // 에러 코드를 "suspended_account"로 지정해 OAuth2LoginFailureHandler가 구분해서 리다이렉트한다.
        if (member.isSuspended()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("suspended_account"), "정지된 계정입니다.");
        }

        // Authentication.getName()/Principal.getName()이 provider의 PK(sub)가 아닌
        // 이메일을 반환하도록 name attribute key를 "email"로 고정한다.
        // (일반 로그인의 UserDetails.getUsername()도 이메일이므로, 로그인 방식과 무관하게
        //  컨트롤러에서 Principal.getName()을 그대로 이메일로 사용할 수 있게 된다.)
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(member.getRole().getKey())),
                attributes,
                "email");
    }

    private Member saveOrUpdate(String email, String nickname, String profileImage, String provider, String providerId) {
        Member member = memberRepository.findByEmail(email)
                .map(entity -> { // 기존 회원이면 프로필 이미지 및 닉네임만 최신화 (원할 경우)
                    return entity;
                })
                .orElseGet(() -> Member.builder() // 신규 회원이면 구글 정보로 자동 회원가입
                        .email(email)
                        .nickname(nickname)
                        .profileImage(profileImage)
                        .role(Role.USER)
                        .provider(provider)
                        .providerId(providerId)
                        .build());

        return memberRepository.save(member);
    }
}