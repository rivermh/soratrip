package com.rivermh.soratrip.domain.comment.service;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.dto.CommentResponseDto;
import com.rivermh.soratrip.domain.comment.entity.Comment;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    // 댓글 작성 (parentId가 있으면 답글)
    @Transactional
    public Long createComment(Long postId, CommentRequestDto dto, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));

        Member writer = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Comment parent = null;
        if (dto.getParentId() != null) {
            parent = commentRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("답글을 달 댓글이 존재하지 않습니다. id=" + dto.getParentId()));
            if (!parent.getPost().getId().equals(postId)) {
                throw new IllegalArgumentException("잘못된 댓글 정보입니다.");
            }
        }

        Comment comment = Comment.builder()
                .content(dto.getContent())
                .post(post)
                .writer(writer)
                .parent(parent)
                .build();

        Long commentId = commentRepository.save(comment).getId();
        notificationService.notify(post.getWriter(), writer, NotificationType.POST_COMMENT, post.getId(), post.getTitle());
        // 답글인 경우, 게시글 작성자와 별개로 원 댓글 작성자에게도 알림 (중복 알림 방지)
        if (parent != null && !parent.getWriter().getId().equals(post.getWriter().getId())) {
            notificationService.notify(parent.getWriter(), writer, NotificationType.COMMENT_REPLY, post.getId(), post.getTitle());
        }
        return commentId;
    }

    // 댓글 수정
    @Transactional
    public void updateComment(Long commentId, CommentRequestDto dto, String userEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다. id=" + commentId));

        if (!comment.getWriter().getEmail().equals(userEmail)) {
            throw new IllegalStateException("본인의 댓글만 수정할 수 있습니다.");
        }

        comment.updateContent(dto.getContent());
    }

    // 게시글의 댓글 목록 조회
 // 게시글의 댓글 목록 조회 (로그인 유저 이메일 수신)
    public List<CommentResponseDto> getComments(Long postId, String loginUserEmail) {
        return commentRepository.findByPostIdWithWriter(postId).stream()
                .map(comment -> new CommentResponseDto(comment, loginUserEmail))
                .collect(Collectors.toList());
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long commentId, String userEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다. id=" + commentId));

        if (!comment.getWriter().getEmail().equals(userEmail)) {
            throw new IllegalStateException("본인의 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
    }
}