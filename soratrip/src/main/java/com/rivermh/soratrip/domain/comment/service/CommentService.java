package com.rivermh.soratrip.domain.comment.service;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.dto.CommentResponseDto;
import com.rivermh.soratrip.domain.comment.entity.Comment;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
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

    // 댓글 작성
    @Transactional
    public Long createComment(Long postId, CommentRequestDto dto, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));
        
        Member writer = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Comment comment = Comment.builder()
                .content(dto.getContent())
                .post(post)
                .writer(writer)
                .build();

        return commentRepository.save(comment).getId();
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