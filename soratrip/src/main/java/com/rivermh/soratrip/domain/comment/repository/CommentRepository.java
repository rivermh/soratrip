package com.rivermh.soratrip.domain.comment.repository;

import com.rivermh.soratrip.domain.comment.entity.Comment;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	// N+1 문제 방지를 위한 Fetch Join (작성자 + 부모 댓글 작성자 정보 포함)
	@Query("SELECT c FROM Comment c JOIN FETCH c.writer LEFT JOIN FETCH c.parent p LEFT JOIN FETCH p.writer WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
	List<Comment> findByPostIdWithWriter(@Param("postId") Long postId);

	// 내가 작성한 댓글 목록 (최신순)
	List<Comment> findByWriterEmailOrderByIdDesc(String email);

	// 회원 탈퇴용: 탈퇴 회원이 쓴 글에 달린 댓글 일괄 삭제
	void deleteByPostIn(List<Post> posts);

	// 회원 탈퇴용: 탈퇴 회원이 남긴 댓글 일괄 삭제
	void deleteByWriter(Member writer);
}