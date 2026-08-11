package com.rivermh.soratrip.domain.post.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.PostApplication;

public interface PostApplicationRepository extends JpaRepository<PostApplication, Long> {

    boolean existsByPostAndApplicant(Post post, Member applicant);

    Optional<PostApplication> findByPostAndApplicant(Post post, Member applicant);

    List<PostApplication> findByPostOrderByIdAsc(Post post);

    void deleteByPostIn(List<Post> posts);

    void deleteByApplicant(Member applicant);
}
