package com.rivermh.soratrip.domain.comment.dto;

import com.rivermh.soratrip.domain.comment.entity.Comment;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CommentResponseDto {

    private Long id;
    private String content;
    private String writerNickname;
    private String writerEmail;
    private LocalDateTime createdAt;
    private boolean owner; // 현재 사용자가 작성자인지 여부
    private Long parentId;
    private String parentWriterNickname;
    private boolean edited;

    public CommentResponseDto(Comment comment, String loginUserEmail) {
        this.id = comment.getId();
        this.content = comment.getContent();
        this.writerNickname = comment.getWriter().getNickname();
        this.writerEmail = comment.getWriter().getEmail();
        this.createdAt = comment.getCreatedAt();
        // 로그인한 이메일과 작성자 이메일 비교 (비로그인 시 null 처리)
        this.owner = loginUserEmail != null && loginUserEmail.equals(this.writerEmail);
        this.parentId = comment.getParent() != null ? comment.getParent().getId() : null;
        this.parentWriterNickname = comment.getParent() != null ? comment.getParent().getWriter().getNickname() : null;
        this.edited = comment.getUpdatedAt() != null && !comment.getUpdatedAt().equals(comment.getCreatedAt());
    }
}