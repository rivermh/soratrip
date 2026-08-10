package com.rivermh.soratrip.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberProfileDto {

    private String email;

    @NotBlank(message = "{member.validation.nickname_required}")
    @Size(min = 2, max = 10, message = "{member.validation.nickname_size}")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9_]*$", message = "{member.validation.nickname_pattern}")
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