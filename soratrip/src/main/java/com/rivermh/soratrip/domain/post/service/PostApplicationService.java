package com.rivermh.soratrip.domain.post.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.chat.service.ChatService;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import com.rivermh.soratrip.domain.post.dto.PostApplicationResponseDto;
import com.rivermh.soratrip.domain.post.entity.ApplicationStatus;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.PostApplication;
import com.rivermh.soratrip.domain.post.repository.PostApplicationRepository;
import com.rivermh.soratrip.domain.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostApplicationService {

    private final PostApplicationRepository postApplicationRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final ChatService chatService;

    // 동행 신청
    @Transactional
    public Long apply(Long postId, String applicantEmail, String message) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));
        Member applicant = memberRepository.findByEmail(applicantEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (post.getWriter().getId().equals(applicant.getId())) {
            throw new IllegalStateException("본인이 작성한 글에는 신청할 수 없습니다.");
        }
        if (postApplicationRepository.existsByPostAndApplicant(post, applicant)) {
            throw new IllegalStateException("이미 신청한 게시글입니다.");
        }

        PostApplication application = PostApplication.builder()
                .post(post)
                .applicant(applicant)
                .message(message)
                .build();
        Long applicationId = postApplicationRepository.save(application).getId();

        notificationService.notify(post.getWriter(), applicant, NotificationType.POST_APPLICATION, post.getId(), post.getTitle());
        return applicationId;
    }

    // 신청 수락: 신청 상태를 ACCEPTED로 바꾸고, 글쓴이-신청자 사이에 채팅방을 자동으로 개설한다
    @Transactional
    public void accept(Long applicationId, String ownerEmail) {
        PostApplication application = getOwnedPendingApplication(applicationId, ownerEmail);
        application.accept();

        Member owner = application.getPost().getWriter();
        Member applicant = application.getApplicant();
        chatService.getOrCreateChatRoom(owner.getId(), applicant.getId());

        notificationService.notify(applicant, owner, NotificationType.APPLICATION_ACCEPTED,
                application.getPost().getId(), application.getPost().getTitle());
    }

    // 신청 거절
    @Transactional
    public void reject(Long applicationId, String ownerEmail) {
        PostApplication application = getOwnedPendingApplication(applicationId, ownerEmail);
        application.reject();

        notificationService.notify(application.getApplicant(), application.getPost().getWriter(),
                NotificationType.APPLICATION_REJECTED, application.getPost().getId(), application.getPost().getTitle());
    }

    private PostApplication getOwnedPendingApplication(Long applicationId, String ownerEmail) {
        PostApplication application = postApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청 내역이 존재하지 않습니다. id=" + applicationId));
        if (!application.getPost().getWriter().getEmail().equals(ownerEmail)) {
            throw new IllegalStateException("본인의 게시글에 대한 신청만 처리할 수 있습니다.");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }
        return application;
    }

    // 글쓴이 전용: 해당 게시글에 들어온 신청 목록
    public List<PostApplicationResponseDto> getApplications(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));
        return postApplicationRepository.findByPostOrderByIdAsc(post).stream()
                .map(PostApplicationResponseDto::new)
                .collect(Collectors.toList());
    }

    // 로그인한 조회자 본인이 이 게시글에 신청한 내역(있다면)
    public Optional<PostApplicationResponseDto> getMyApplication(Long postId, String applicantEmail) {
        if (applicantEmail == null) {
            return Optional.empty();
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));
        Member applicant = memberRepository.findByEmail(applicantEmail).orElse(null);
        if (applicant == null) {
            return Optional.empty();
        }
        return postApplicationRepository.findByPostAndApplicant(post, applicant)
                .map(PostApplicationResponseDto::new);
    }
}
