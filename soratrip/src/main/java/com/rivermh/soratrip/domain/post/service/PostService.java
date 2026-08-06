package com.rivermh.soratrip.domain.post.service;

import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.dto.PostCreateDto;
import com.rivermh.soratrip.domain.post.dto.PostResponseDto;
import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostLikeRepository postLikeRepository; //  1. 좋아요 리포지토리

    // 글 작성
    @Transactional
    public Long createPost(PostCreateDto dto, String userEmail) {
        Member writer = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Post post = Post.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .region(dto.getRegion())
                .category(dto.getCategory())
                .travelStartDate(dto.getTravelStartDate())
                .travelEndDate(dto.getTravelEndDate())
                .recruitmentCount(dto.getRecruitmentCount())
                .writer(writer)
                .build();

        if (dto.getLatitude() != null && dto.getLongitude() != null) {
            post.updateLocation(dto.getPlaceName(), dto.getPlaceAddress(), dto.getLatitude(), dto.getLongitude());
        }

        return postRepository.save(post).getId();
    }

    // 전체 글 목록 (지역/카테고리 필터링 포함)
    public List<PostResponseDto> getPosts(Region region, Category category) {
        List<Post> posts;
        if (region != null) {
            posts = postRepository.findByRegionOrderByCreatedAtDesc(region);
        } else if (category != null) {
            posts = postRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else {
            posts = postRepository.findAllWithWriter();
        }

        // 목록 조회 시 각 게시글의 좋아요 수를 넘겨준다
        return posts.stream()
                .map(post -> {
                    int likeCount = postLikeRepository.countByPost(post);
                    return new PostResponseDto(post, likeCount, false);
                })
                .collect(Collectors.toList());
    }

    // 게시글 상세 조회 (+ 조회수 증가 & 좋아요 정보 바인딩)
    @Transactional
    public PostResponseDto getPostDetail(Long id, String userEmail) { // 👈 3. 파라미터에 userEmail 추가 (비로그인은 null)
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));

        post.increaseViewCount(); // 조회수 1 증가

        // DB에서 좋아요 수와 로그인 사용자의 좋아요 눌렀는지 여부 조회
        int likeCount = postLikeRepository.countByPost(post);
        boolean liked = false;

        if (userEmail != null && !userEmail.isBlank()) {
            liked = memberRepository.findByEmail(userEmail)
                    .map(member -> postLikeRepository.existsByMemberAndPost(member, post))
                    .orElse(false);
        }

        return new PostResponseDto(post, likeCount, liked);
    }

    // 기존 단일 id 기반 getPostDetail 하위 호환용 (비로그인 사용자용)
    @Transactional
    public PostResponseDto getPostDetail(Long id) {
        return getPostDetail(id, null);
    }

    // 검색 및 페이징 목록 조회
    public Page<PostResponseDto> getPostList(Region region, Category category, String keyword, Pageable pageable) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Post> posts = postRepository.searchPosts(region, category, searchKeyword, pageable);

        // 👈 4. 페이징 목록 DTO 변환 시 좋아요 수 같이 바인딩
        return posts.map(post -> {
            int likeCount = postLikeRepository.countByPost(post);
            return new PostResponseDto(post, likeCount, false);
        });
    }
}