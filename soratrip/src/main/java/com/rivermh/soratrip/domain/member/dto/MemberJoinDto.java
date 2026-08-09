package com.rivermh.soratrip.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberJoinDto {

    @NotBlank(message = "{member.validation.email_required}")
    @Email(message = "{member.validation.email_invalid}")
    private String email;

    @NotBlank(message = "{member.validation.password_required}")
    @Size(min = 8, max = 20, message = "{member.validation.password_size}")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
             message = "{member.validation.password_pattern}")
    private String password;

    @NotBlank(message = "{member.validation.password_confirm_required}")
    private String passwordConfirm;

    @NotBlank(message = "{member.validation.nickname_required}")
    @Size(min = 2, max = 10, message = "{member.validation.nickname_size}")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9_]*$", message = "{member.validation.nickname_pattern}")
    private String nickname;

    // 비밀번호와 비밀번호 확인이 일치하는지 체크하는 헬퍼 메서드
    public boolean isPasswordMatched() {
        return password != null && password.equals(passwordConfirm);
    }
}