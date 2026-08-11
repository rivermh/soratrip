package com.rivermh.soratrip.domain.post.dto;

import java.time.LocalDateTime;

import com.rivermh.soratrip.domain.post.entity.ApplicationStatus;
import com.rivermh.soratrip.domain.post.entity.PostApplication;

import lombok.Getter;

@Getter
public class PostApplicationResponseDto {

    private final Long id;
    private final String applicantNickname;
    private final String message;
    private final ApplicationStatus status;
    private final LocalDateTime createdAt;

    public PostApplicationResponseDto(PostApplication application) {
        this.id = application.getId();
        this.applicantNickname = application.getApplicant().getNickname();
        this.message = application.getMessage();
        this.status = application.getStatus();
        this.createdAt = application.getCreatedAt();
    }
}
