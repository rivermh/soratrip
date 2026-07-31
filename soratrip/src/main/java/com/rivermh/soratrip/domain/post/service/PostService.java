package com.rivermh.soratrip.domain.post.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

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
        
     // 위치 데이터가 들어온 경우 엔티티에 세팅
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

        return posts.stream()
                .map(PostResponseDto::new)
                .collect(Collectors.toList());
    }

    // 게시글 상세 조회 (+ 조회수 증가)
    @Transactional
    public PostResponseDto getPostDetail(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + id));
        
        post.increaseViewCount(); // 조회수 1 증가
        return new PostResponseDto(post);
    }
    
 // 검색 및 페이징 목록 조회
    public Page<PostResponseDto> getPostList(Region region, Category category, String keyword, Pageable pageable) {
        // 빈 문자열 키워드는 null 처리
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        
        Page<Post> posts = postRepository.searchPosts(region, category, searchKeyword, pageable);
        return posts.map(PostResponseDto::new);
    }
}