package com.rivermh.soratrip.domain.post.service;

import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.dto.PostCreateDto;
import com.rivermh.soratrip.domain.post.dto.PostResponseDto;
import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.post.repository.PostApplicationRepository;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
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
    private final PostViewRedisService postViewRedisService;
    private final CommentRepository commentRepository;
    private final PostApplicationRepository postApplicationRepository;

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
    public PostResponseDto getPostDetail(Long id, String userEmail, String viewerKey) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));

        // 같은 사용자(로그인 이메일 또는 세션)가 짧은 시간 안에 재조회한 경우가 아니면 조회수 증가
        if (!postViewRedisService.isRecentlyViewed(id, viewerKey)) {
            post.increaseViewCount();
        }

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

    // 게시글 수정 폼 진입용 조회 (작성자 본인만 가능)
    public PostCreateDto getPostForEdit(Long id, String userEmail) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));

        if (!post.getWriter().getEmail().equals(userEmail)) {
            throw new IllegalStateException("해당 게시글을 수정할 권한이 없습니다.");
        }

        PostCreateDto dto = new PostCreateDto();
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setRegion(post.getRegion());
        dto.setCategory(post.getCategory());
        dto.setTravelStartDate(post.getTravelStartDate());
        dto.setTravelEndDate(post.getTravelEndDate());
        dto.setRecruitmentCount(post.getRecruitmentCount());
        dto.setPlaceName(post.getPlaceName());
        dto.setPlaceAddress(post.getPlaceAddress());
        dto.setLatitude(post.getLatitude());
        dto.setLongitude(post.getLongitude());
        return dto;
    }

    // 게시글 수정 (작성자 본인만 가능)
    @Transactional
    public void updatePost(Long id, PostCreateDto dto, String userEmail) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));

        if (!post.getWriter().getEmail().equals(userEmail)) {
            throw new IllegalStateException("해당 게시글을 수정할 권한이 없습니다.");
        }

        post.update(dto.getTitle(), dto.getContent(), dto.getRegion(), dto.getCategory(),
                dto.getTravelStartDate(), dto.getTravelEndDate(), dto.getRecruitmentCount());

        if (dto.getLatitude() != null && dto.getLongitude() != null) {
            post.updateLocation(dto.getPlaceName(), dto.getPlaceAddress(), dto.getLatitude(), dto.getLongitude());
        }
    }

    // 게시글 삭제 (작성자 본인만 가능, 댓글/좋아요/신청 내역도 함께 정리)
    @Transactional
    public void deletePost(Long id, String userEmail) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));

        if (!post.getWriter().getEmail().equals(userEmail)) {
            throw new IllegalStateException("해당 게시글을 삭제할 권한이 없습니다.");
        }

        List<Post> posts = List.of(post);
        commentRepository.deleteByPostIn(posts);
        postLikeRepository.deleteByPostIn(posts);
        postApplicationRepository.deleteByPostIn(posts);
        postRepository.delete(post);
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