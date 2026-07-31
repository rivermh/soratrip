package com.rivermh.soratrip.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateDto {

    private String nickname;
    private String languageLevel;
    private String bio;

    public MemberUpdateDto(String nickname, String languageLevel, String bio) {
        this.nickname = nickname;
        this.languageLevel = languageLevel;
        this.bio = bio;
    }
}