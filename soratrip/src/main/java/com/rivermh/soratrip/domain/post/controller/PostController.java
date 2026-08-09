package com.rivermh.soratrip.domain.post.controller;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.dto.CommentResponseDto;
import com.rivermh.soratrip.domain.comment.service.CommentService;
import com.rivermh.soratrip.domain.post.dto.PostCreateDto;
import com.rivermh.soratrip.domain.post.dto.PostResponseDto;
import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.post.service.PostService;
import com.rivermh.soratrip.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;

import java.util.List;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;
	private final CommentService commentService;

	// 1. 게시글 목록 조회 (필터링 지원)
	@GetMapping
	public String postList(@RequestParam(name = "region", required = false) Region region,
						   @RequestParam(name = "category", required = false) Category category,
						   @RequestParam(name = "keyword", required = false) String keyword,
						   @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
						   Model model) {

		Page<PostResponseDto> posts = postService.getPostList(region, category, keyword, pageable);

		model.addAttribute("posts", posts);
		model.addAttribute("regions", Region.values());
		model.addAttribute("categories", Category.values());
		
		// 검색 필터 상태 유지용
		model.addAttribute("selectedRegion", region);
		model.addAttribute("selectedCategory", category);
		model.addAttribute("keyword", keyword);

		return "post/list";
	}

	// 2. 게시글 작성 폼 이동
	@GetMapping("/new")
	public String createForm(Model model) {
		model.addAttribute("postForm", new PostCreateDto());
		model.addAttribute("regions", Region.values());
		model.addAttribute("categories", Category.values());
		return "post/form";
	}

	// 3. 게시글 작성 처리
	@PostMapping("/new")
	public String createPost(@ModelAttribute("postForm") PostCreateDto dto, @AuthenticationPrincipal Object principal) {
		String email = SecurityUtils.extractEmail(principal);
		Long postId = postService.createPost(dto, email);
		return "redirect:/posts/" + postId;
	}

	// 4. 게시글 상세 페이지 
	@GetMapping("/{id}")
	public String postDetail(@PathVariable(name = "id") Long id, 
							 @AuthenticationPrincipal Object principal, 
							 Model model) {
		
		// 💡 1. 이메일 추출을 먼저 수행 (비로그인 사용자는 null)
		String loginEmail = null;
		if (principal != null) {
			loginEmail = SecurityUtils.extractEmail(principal);
		}

		// 💡 2. postService.getPostDetail에 loginEmail 전달 (좋아요 여부 판단용)
		PostResponseDto post = postService.getPostDetail(id, loginEmail);
		List<CommentResponseDto> comments = commentService.getComments(id, loginEmail);
		
		model.addAttribute("post", post);
		model.addAttribute("comments", comments);
		model.addAttribute("commentForm", new CommentRequestDto());
		return "post/detail";
	}
}