package com.rivermh.soratrip.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberProfileDto {

    private String email;
    private String nickname;
    private String profileImage;
    private String bio;
    private String languageLevel;
    private String nationality;

    public MemberProfileDto(String email, String nickname, String profileImage, String bio, String languageLevel, String nationality) {
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.bio = bio;
        this.languageLevel = languageLevel;
        this.nationality = nationality;
    }
}