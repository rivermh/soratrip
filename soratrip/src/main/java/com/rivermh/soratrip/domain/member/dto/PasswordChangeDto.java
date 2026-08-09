package com.rivermh.soratrip.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 마이페이지 비밀번호 변경 (로그인 상태, 현재 비밀번호 확인 필요)
@Getter
@Setter
public class PasswordChangeDto {

    @NotBlank(message = "{member.mypage.password.current_required}")
    private String currentPassword;

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
