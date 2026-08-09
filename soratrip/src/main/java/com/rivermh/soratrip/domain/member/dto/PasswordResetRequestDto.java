package com.rivermh.soratrip.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 비밀번호 재설정 1단계: 이메일 입력 → 인증 코드 발송
@Getter
@Setter
public class PasswordResetRequestDto {

    @NotBlank(message = "{member.validation.email_required}")
    @Email(message = "{member.validation.email_invalid}")
    private String email;
}
