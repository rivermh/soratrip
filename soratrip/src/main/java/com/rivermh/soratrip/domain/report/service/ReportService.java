package com.rivermh.soratrip.domain.report.service;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.report.entity.PostReport;
import com.rivermh.soratrip.domain.report.entity.ReportStatus;
import com.rivermh.soratrip.domain.report.repository.PostReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final PostReportRepository postReportRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    // 게시글 신고. 이미 신고한 경우 false를 반환해 컨트롤러가 안내 메시지로 분기하도록 한다
    // (예외로 처리하기엔 사용자가 실수로 두 번 누르는 흔한 케이스라 500까지 갈 필요 없음).
    @Transactional
    public boolean reportPost(Long postId, String reason, String reporterEmail) {
        Member reporter = memberRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));

        if (post.getWriter().getEmail().equals(reporterEmail)) {
            throw new IllegalStateException("본인 게시글은 신고할 수 없습니다.");
        }

        if (postReportRepository.existsByReporterAndPost(reporter, post)) {
            return false;
        }

        postReportRepository.save(PostReport.builder().reporter(reporter).post(post).reason(reason).build());
        return true;
    }

    public Page<PostReport> getPendingReports(Pageable pageable) {
        return postReportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable);
    }

    @Transactional
    public void resolveReport(Long reportId) {
        PostReport report = postReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다."));
        report.resolve();
    }

    public long countPending() {
        return postReportRepository.countByStatus(ReportStatus.PENDING);
    }
}
