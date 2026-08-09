package com.rivermh.soratrip.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 비밀번호 재설정 2단계: 인증 코드 + 새 비밀번호 입력
@Getter
@Setter
public class PasswordResetConfirmDto {

    @NotBlank(message = "{member.validation.email_required}")
    @Email(message = "{member.validation.email_invalid}")
    private String email;

    @NotBlank(message = "{member.password_reset.code_required}")
    private String code;

    @NotBlank(message = "{member.validation.password_required}")
    @Size(min = 8, max = 20, message = "{member.validation.password_size}")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
             message = "{member.validation.password_pattern}")
    private String newPassword;

    @NotBlank(message = "{member.validation.password_confirm_required}")
    private String newPasswordConfirm;

    public boolean isPasswordMatched() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
