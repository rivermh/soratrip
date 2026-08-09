package com.rivermh.soratrip.domain.like.service;

import com.rivermh.soratrip.domain.like.dto.LikeToggleResponse;
import com.rivermh.soratrip.domain.like.entity.PostLike;
import com.rivermh.soratrip.domain.like.entity.ScheduleLike;
import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.like.repository.ScheduleLikeRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final ScheduleLikeRepository scheduleLikeRepository;
    private final PostRepository postRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    // 게시글 좋아요 토글
    public LikeToggleResponse togglePostLike(Long postId, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Optional<PostLike> postLike = postLikeRepository.findByMemberAndPost(member, post);
        boolean isLiked;

        if (postLike.isPresent()) {
            postLikeRepository.delete(postLike.get());
            isLiked = false;
        } else {
            postLikeRepository.save(PostLike.builder().member(member).post(post).build());
            isLiked = true;
            notificationService.notify(post.getWriter(), member, NotificationType.POST_LIKE, post.getId(), post.getTitle());
        }

        int currentLikeCount = postLikeRepository.countByPost(post);
        return new LikeToggleResponse(isLiked, currentLikeCount);
    }

    // 여행 일정 좋아요 토글 (💡 복구된 메서드)
    public LikeToggleResponse toggleScheduleLike(Long scheduleId, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        Optional<ScheduleLike> scheduleLike = scheduleLikeRepository.findByMemberAndTravelSchedule(member, schedule);
        boolean isLiked;

        if (scheduleLike.isPresent()) {
            scheduleLikeRepository.delete(scheduleLike.get());
            isLiked = false;
        } else {
            scheduleLikeRepository.save(ScheduleLike.builder().member(member).travelSchedule(schedule).build());
            isLiked = true;
            notificationService.notify(schedule.getMember(), member, NotificationType.SCHEDULE_LIKE, schedule.getId(), schedule.getTitle());
        }

        int currentLikeCount = scheduleLikeRepository.countByTravelSchedule(schedule);
        return new LikeToggleResponse(isLiked, currentLikeCount);
    }
}