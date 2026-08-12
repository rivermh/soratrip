package com.rivermh.soratrip.domain.report.repository;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.report.entity.PostReport;
import com.rivermh.soratrip.domain.report.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    boolean existsByReporterAndPost(Member reporter, Post post);

    // 게시글 삭제용: 게시글에 달린 신고 내역 일괄 삭제
    void deleteByPostIn(List<Post> posts);

    // 회원 탈퇴/삭제용: 그 회원이 접수한 신고 내역 일괄 삭제
    void deleteByReporter(Member reporter);

    Page<PostReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);
}
