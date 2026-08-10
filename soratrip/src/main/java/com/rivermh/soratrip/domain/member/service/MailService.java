package com.rivermh.soratrip.domain.member.service;

import java.security.SecureRandom;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    // Resend 발신 주소. 커스텀 도메인을 인증하기 전까지는 Resend의 샌드박스 발신 주소만 사용 가능
    // (샌드박스 상태에서는 Resend 계정에 등록된 이메일로만 수신 가능)
    @Value("${mail.from:onboarding@resend.dev}")
    private String fromAddress;

    private static final String CODE_PREFIX = "email:code:";
    private static final String VERIFIED_PREFIX = "email:verified:";
    private static final long CODE_EXPIRATION_TIME = 180L; // 3분
    private static final long VERIFIED_EXPIRATION_TIME = 1800L; // 30분

    private static final String PWD_RESET_CODE_PREFIX = "pwd-reset:";
    private static final long PWD_RESET_CODE_EXPIRATION_TIME = 180L; // 3분

    private static final String PWD_RESET_ATTEMPT_PREFIX = "pwd-reset-attempt:";
    private static final int PWD_RESET_ATTEMPT_THRESHOLD = 5;

    // 코드 "검증" 시도 횟수 제한(PWD_RESET_ATTEMPT)과는 별개로, 코드 "발송 요청" 자체를 제한한다.
    // 제한이 없으면 공격자가 타인의 이메일로 재설정 요청을 반복 호출해 메일함을 스팸으로 채우거나
    // (email bombing) 메일 발송 쿼터를 소진시킬 수 있다.
    private static final String PWD_RESET_REQUEST_PREFIX = "pwd-reset-request:";
    private static final int PWD_RESET_REQUEST_THRESHOLD = 5;
    private static final long PWD_RESET_REQUEST_WINDOW_SECONDS = 3600L; // 1시간

    /**
     * 6자리 난수 인증 코드 생성 후 메일 발송 및 Redis 저장
     */
    public void sendVerificationCode(String email) {
        String code = createCode();

        // Redis 저장 (Key: email:code:user@email.com, Value: 123456, TTL: 3분)
        redisTemplate.opsForValue().set(
                CODE_PREFIX + email,
                code,
                Duration.ofSeconds(CODE_EXPIRATION_TIME)
        );

        sendMail(email, "[Soratrip] 이메일 인증 코드 안내", "인증 코드: " + code + "\n유효시간은 3분입니다.");
    }

    /**
     * 인증 코드 검증
     */
    public boolean verifyCode(String email, String inputCode) {
        String savedCode = redisTemplate.opsForValue().get(CODE_PREFIX + email);

        if (savedCode != null && savedCode.equals(inputCode)) {
            // 인증 성공 시 사용한 코드 삭제
            redisTemplate.delete(CODE_PREFIX + email);
            // 인증 완료 상태 저장 (TTL: 30분)
            redisTemplate.opsForValue().set(
                    VERIFIED_PREFIX + email,
                    "true",
                    Duration.ofSeconds(VERIFIED_EXPIRATION_TIME)
            );
            return true;
        }
        return false;
    }

    /**
     * 이메일 인증 완료 상태 확인
     */
    public boolean isEmailVerified(String email) {
        String isVerified = redisTemplate.opsForValue().get(VERIFIED_PREFIX + email);
        return "true".equals(isVerified);
    }

    /**
     * 회원가입 완료 후 인증 상태 삭제
     */
    public void clearVerificationStatus(String email) {
        redisTemplate.delete(VERIFIED_PREFIX + email);
    }

    /**
     * 비밀번호 재설정 코드 생성 후 메일 발송 및 Redis 저장 (가입 시 이메일 인증 코드와 동일한 방식/TTL)
     */
    public void sendPasswordResetCode(String email) {
        checkPasswordResetRequestRate(email);

        String code = createCode();

        redisTemplate.opsForValue().set(
                PWD_RESET_CODE_PREFIX + email,
                code,
                Duration.ofSeconds(PWD_RESET_CODE_EXPIRATION_TIME)
        );
        // 새 코드를 발급하면 이전 코드에 대한 시도 횟수는 더 이상 의미가 없으므로 초기화한다.
        redisTemplate.delete(PWD_RESET_ATTEMPT_PREFIX + email);

        sendMail(email, "[Soratrip] 비밀번호 재설정 인증 코드 안내", "비밀번호 재설정 인증 코드: " + code + "\n유효시간은 3분입니다.");
    }

    /**
     * 비밀번호 재설정 코드에 대한 시도 횟수가 한도(5회)를 초과했는지 확인한다.
     * 코드 검증 전에 먼저 호출해, 잠긴 상태에서는 코드 비교 자체를 시도하지 않는다.
     */
    public boolean isPasswordResetLocked(String email) {
        String count = redisTemplate.opsForValue().get(PWD_RESET_ATTEMPT_PREFIX + email);
        return count != null && Integer.parseInt(count) >= PWD_RESET_ATTEMPT_THRESHOLD;
    }

    /**
     * 비밀번호 재설정 코드 검증. 성공 시 재사용 방지를 위해 코드와 시도 횟수 카운터를 즉시 삭제한다.
     * 실패 시 시도 횟수를 누적시키며(코드와 동일한 TTL), 한도 도달 시 {@link #isPasswordResetLocked}가 true를 반환하게 된다.
     */
    public boolean verifyPasswordResetCode(String email, String inputCode) {
        String savedCode = redisTemplate.opsForValue().get(PWD_RESET_CODE_PREFIX + email);

        if (savedCode != null && savedCode.equals(inputCode)) {
            redisTemplate.delete(PWD_RESET_CODE_PREFIX + email);
            redisTemplate.delete(PWD_RESET_ATTEMPT_PREFIX + email);
            return true;
        }

        recordPasswordResetAttempt(email);
        return false;
    }

    /**
     * 이메일당 시간당 비밀번호 재설정 코드 발송 요청 횟수를 제한한다.
     * 한도 초과 시 예외를 던지며, 호출부(MemberController)는 이미 모든 예외를
     * 동일한 화면으로 조용히 흡수하므로 계정 열거로는 이어지지 않는다.
     */
    private void checkPasswordResetRequestRate(String email) {
        String key = PWD_RESET_REQUEST_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(PWD_RESET_REQUEST_WINDOW_SECONDS));
        }
        if (count != null && count > PWD_RESET_REQUEST_THRESHOLD) {
            throw new IllegalStateException("비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void recordPasswordResetAttempt(String email) {
        String key = PWD_RESET_ATTEMPT_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(PWD_RESET_CODE_EXPIRATION_TIME));
        }
    }

    private void sendMail(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("메일 발송에 실패했습니다.", e);
        }
    }

    private String createCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}