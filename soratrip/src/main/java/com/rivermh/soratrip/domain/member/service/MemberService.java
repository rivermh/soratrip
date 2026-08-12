package com.rivermh.soratrip.domain.member.service;

import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.bookmark.repository.ScheduleBookmarkRepository;
import com.rivermh.soratrip.domain.chat.entity.ChatRoom;
import com.rivermh.soratrip.domain.chat.repository.ChatMessageRepository;
import com.rivermh.soratrip.domain.chat.repository.ChatRoomRepository;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.like.repository.ScheduleLikeRepository;
import com.rivermh.soratrip.domain.member.dto.MemberJoinDto;
import com.rivermh.soratrip.domain.member.dto.MemberProfileDto;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.repository.NotificationRepository;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.repository.PostApplicationRepository;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import com.rivermh.soratrip.domain.settlement.service.SettlementCleanupService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final TravelScheduleRepository travelScheduleRepository;
    private final SettlementCleanupService settlementCleanupService;
    private final ScheduleLikeRepository scheduleLikeRepository;
    private final ScheduleBookmarkRepository scheduleBookmarkRepository;
    private final PostRepository postRepository;
    private final PostApplicationRepository postApplicationRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 회원가입
     * 
     * @param dto 회원가입 정보
     * @return 생성된 회원 ID
     */
    @Transactional
    public Long join(MemberJoinDto dto) {
        if (!dto.isPasswordMatched()) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (memberRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // 이메일 선-인증 완료 여부 검증
        if (!mailService.isEmailVerified(dto.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        Member member = Member.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .role(Role.USER)
                .provider("local")
                .build();

        Long memberId = memberRepository.save(member).getId();

        // 회원가입 성공 후 Redis의 인증 상태 삭제
        mailService.clearVerificationStatus(dto.getEmail());

        return memberId;
    }

    /**
     * 이메일 사용 가능 여부 확인
     * 
     * @param email 확인할 이메일
     * @return 사용 가능하면 true, 중복이면 false
     */
    public boolean isEmailAvailable(String email) {
        return memberRepository.findByEmail(email).isEmpty();
    }

    /**
     * 프로필 조회
     * 
     * @param email 회원 이메일
     * @return 회원 프로필 정보
     */
    public MemberProfileDto getProfile(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return new MemberProfileDto(
                member.getEmail(),
                member.getNickname(),
                member.getProfileImage(),
                member.getBio(),
                member.getLanguageLevel(),
                member.getNationality()
        );
    }

    /**
     * 프로필 수정
     * 
     * @param email 회원 이메일
     * @param dto   수정할 프로필 정보
     */
    @Transactional
    public void updateProfile(String email, MemberProfileDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updateProfile(
                dto.getNickname(),
                dto.getBio(),
                dto.getLanguageLevel(),
                dto.getNationality()
        );
    }

    /**
     * 여행 취향 태그 수정
     * 
     * @param email         회원 이메일
     * @param preferredTags 선택한 여행 태그들
     */
    @Transactional
    public void updatePreferredTags(String email, Set<ScheduleTag> preferredTags) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updatePreferredTags(preferredTags);
    }

    /**
     * 여행 취향 태그 조회
     * 
     * @param email 회원 이메일
     * @return 회원이 선택한 여행 태그들
     */
    public Set<ScheduleTag> getPreferredTags(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return member.getPreferredTags();
    }

    /**
     * 비밀번호 재설정 요청 (비로그인 상태) — 인증 코드 발송
     */
    public void requestPasswordReset(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정은 비밀번호 재설정을 이용할 수 없습니다.");
        }

        mailService.sendPasswordResetCode(email);
    }

    /**
     * 비밀번호 재설정 확정 (비로그인 상태) — 인증 코드 검증 후 비밀번호를 변경한다.
     * 현재 세션 무효화는 HttpServletRequest에 접근 가능한 컨트롤러 계층에서 처리한다.
     */
    @Transactional
    public void confirmPasswordReset(String email, String code, String newPassword) {
        if (mailService.isPasswordResetLocked(email)) {
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 재설정을 처음부터 다시 요청해주세요.");
        }

        if (!mailService.verifyPasswordResetCode(email, code)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않거나 만료되었습니다.");
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updatePassword(passwordEncoder.encode(newPassword));
    }

    /**
     * 마이페이지 비밀번호 변경 (로그인 상태) — 현재 비밀번호를 확인한 뒤 변경한다.
     * 현재 세션 무효화는 HttpServletRequest에 접근 가능한 컨트롤러 계층에서 처리한다.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정은 비밀번호를 변경할 수 없습니다.");
        }

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        member.updatePassword(passwordEncoder.encode(newPassword));
    }

    /**
     * 회원 탈퇴. Member 삭제는 일정(schedules) 쪽으로만 cascade가 걸려 있어서(연쇄적으로
     * ScheduleDay -> ScheduleItem/Expense/Photo/Review까지 정리됨), 그 밖에 FK로만 연결되어
     * cascade가 닿지 않는 데이터(정산 참여자, 다른 사람이 남긴 좋아요/북마크, 게시글, 채팅, 알림)는
     * 여기서 미리 정리한 뒤에 회원을 삭제한다. 순서가 중요하다 — 참조하는 쪽부터 지워야 FK 위반이 안 난다.
     */
    @Transactional
    public void withdraw(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 소셜 로그인 계정은 비밀번호가 없으므로 확인을 건너뛴다
        if (member.getPassword() != null) {
            if (rawPassword == null || !passwordEncoder.matches(rawPassword, member.getPassword())) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
        }

        List<TravelSchedule> schedules = travelScheduleRepository.findByMemberIdOrderByIdDesc(member.getId());
        for (TravelSchedule schedule : schedules) {
            // 정산 참여자(TripParticipant)는 일정에 cascade가 안 걸려 있고, 지출(Expense)의
            // paidBy/sharedWith가 참여자를 참조하므로 참여자를 지우기 전에 먼저 참조를 끊어야 한다
            // (TravelScheduleService.deleteSchedule/SettlementService.removeParticipant와 공유하는 로직)
            settlementCleanupService.detachAllParticipants(schedule.getId());
        }

        if (!schedules.isEmpty()) {
            // 내 일정에 다른 사람이 남긴 좋아요/북마크 (일정 자체는 뒤의 member 삭제 cascade로 정리됨)
            scheduleLikeRepository.deleteByTravelScheduleIn(schedules);
            scheduleBookmarkRepository.deleteByTravelScheduleIn(schedules);
        }

        // 내가 쓴 글(Post)은 Member에 cascade가 없어 직접 정리해야 하고,
        // 그 글에 남이 단 댓글/좋아요도 글보다 먼저 지워야 한다
        List<Post> posts = postRepository.findByWriterEmailOrderByIdDesc(email);
        if (!posts.isEmpty()) {
            commentRepository.deleteByPostIn(posts);
            postLikeRepository.deleteByPostIn(posts);
            postApplicationRepository.deleteByPostIn(posts);
            postRepository.deleteAll(posts);
        }

        // 내가 남의 글/일정에 남긴 활동 흔적
        commentRepository.deleteByWriter(member);
        postLikeRepository.deleteByMember(member);
        scheduleLikeRepository.deleteByMember(member);
        scheduleBookmarkRepository.deleteByMember(member);
        postApplicationRepository.deleteByApplicant(member);

        // 채팅방(상태 무관 - 활성/종료/차단 전부)과 그 메시지들
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByInitiatorOrParticipant(member, member);
        if (!chatRooms.isEmpty()) {
            chatMessageRepository.deleteByChatRoomIn(chatRooms);
            chatRoomRepository.deleteAll(chatRooms);
        }

        // 알림: 내가 받은 알림은 삭제, 내가 발생시킨(actor) 알림은 받는 사람 화면에 남기되 행위자만 익명화
        notificationRepository.deleteByRecipient(member);
        notificationRepository.clearActor(member);

        // 마지막으로 회원 삭제. Member.schedules(cascade=ALL, orphanRemoval=true)를 통해
        // travel_schedule -> schedule_day -> schedule_item/expense/photo/review, 그리고
        // member_preferred_tags(@ElementCollection)까지 전부 정리된다.
        memberRepository.delete(member);
    }
}