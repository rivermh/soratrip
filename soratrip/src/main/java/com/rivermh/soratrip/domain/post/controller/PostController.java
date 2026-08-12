package com.rivermh.soratrip.domain.post.controller;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.dto.CommentResponseDto;
import com.rivermh.soratrip.domain.comment.service.CommentService;
import com.rivermh.soratrip.domain.post.dto.PostApplicationResponseDto;
import com.rivermh.soratrip.domain.post.dto.PostCreateDto;
import com.rivermh.soratrip.domain.post.dto.PostResponseDto;
import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.post.service.PostApplicationService;
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

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;
	private final CommentService commentService;
	private final PostApplicationService postApplicationService;

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
							 HttpServletRequest request,
							 Model model) {

		// 💡 1. 이메일 추출을 먼저 수행 (비로그인 사용자는 null)
		String loginEmail = null;
		if (principal != null) {
			loginEmail = SecurityUtils.extractEmail(principal);
		}

		// 조회수 중복 방지용 식별자: 로그인 사용자는 이메일, 비로그인은 세션ID
		String viewerKey = (loginEmail != null) ? loginEmail : request.getSession().getId();

		// 💡 2. postService.getPostDetail에 loginEmail 전달 (좋아요 여부 판단용)
		PostResponseDto post = postService.getPostDetail(id, loginEmail, viewerKey);
		List<CommentResponseDto> comments = commentService.getComments(id, loginEmail);

		model.addAttribute("post", post);
		model.addAttribute("comments", comments);
		model.addAttribute("commentForm", new CommentRequestDto());

		// 동행구하기(COMPANION) 게시글에 한해 신청 관련 정보를 함께 내려준다.
		// 글쓴이 본인이면 들어온 신청 목록을, 그 외 로그인 사용자면 본인의 신청 여부/상태를 전달한다.
		if (post.getCategory() == Category.COMPANION) {
			boolean isOwner = loginEmail != null && loginEmail.equals(post.getWriterEmail());
			model.addAttribute("isPostOwner", isOwner);
			if (isOwner) {
				model.addAttribute("applications", postApplicationService.getApplications(id));
			} else {
				model.addAttribute("myApplication", postApplicationService.getMyApplication(id, loginEmail).orElse(null));
			}
		}

		return "post/detail";
	}

	// 5. 게시글 수정 폼 이동 (작성자 본인만)
	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable(name = "id") Long id, @AuthenticationPrincipal Object principal, Model model) {
		String email = SecurityUtils.extractEmail(principal);
		PostCreateDto dto = postService.getPostForEdit(id, email);

		model.addAttribute("postForm", dto);
		model.addAttribute("regions", Region.values());
		model.addAttribute("categories", Category.values());
		model.addAttribute("editMode", true);
		model.addAttribute("postId", id);
		return "post/form";
	}

	// 6. 게시글 수정 처리
	@PostMapping("/{id}/edit")
	public String updatePost(@PathVariable(name = "id") Long id, @ModelAttribute("postForm") PostCreateDto dto,
							  @AuthenticationPrincipal Object principal) {
		String email = SecurityUtils.extractEmail(principal);
		postService.updatePost(id, dto, email);
		return "redirect:/posts/" + id;
	}

	// 7. 게시글 삭제 처리
	@PostMapping("/{id}/delete")
	public String deletePost(@PathVariable(name = "id") Long id, @AuthenticationPrincipal Object principal) {
		String email = SecurityUtils.extractEmail(principal);
		postService.deletePost(id, email);
		return "redirect:/posts";
	}
}