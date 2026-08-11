package com.rivermh.soratrip.domain.like.repository;

import com.rivermh.soratrip.domain.like.entity.PostLike;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByMemberAndPost(Member member, Post post);
    boolean existsByMemberAndPost(Member member, Post post);
    
    int countByPost(Post post);

    List<PostLike> findByMemberEmailOrderByIdDesc(String email);

    // 회원 탈퇴용: 탈퇴 회원이 쓴 글에 달린 좋아요 일괄 삭제
    void deleteByPostIn(List<Post> posts);

    // 회원 탈퇴용: 탈퇴 회원이 누른 좋아요 일괄 삭제
    void deleteByMember(Member member);
}