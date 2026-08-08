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
}