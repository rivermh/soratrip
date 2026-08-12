package com.rivermh.soratrip.domain.post.repository;

import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최신순 목록 조회 (작성자 Fetch Join으로 N+1 방지)
    @Query("SELECT p FROM Post p JOIN FETCH p.writer ORDER BY p.createdAt DESC")
    List<Post> findAllWithWriter();

    // 지역별 조회
    List<Post> findByRegionOrderByCreatedAtDesc(Region region);

    // 카테고리별 조회
    List<Post> findByCategoryOrderByCreatedAtDesc(Category category);
    
 // 필터(지역, 카테고리) + 키워드 검색 + 페이징 (N+1 방지를 위해 작성자 Fetch Join)
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.writer " +
           "WHERE (:region IS NULL OR p.region = :region) " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(p) FROM Post p " +
           "WHERE (:region IS NULL OR p.region = :region) " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Post> searchPosts(@Param("region") Region region,
                           @Param("category") Category category,
                           @Param("keyword") String keyword,
                           Pageable pageable);
    
	// 내가 작성한 게시글 목록 (최신순)
	List<Post> findByWriterEmailOrderByIdDesc(String email);

	// 관리자 통계용: 특정 시점 이후 작성된 게시글 수
	long countByCreatedAtAfter(LocalDateTime dateTime);
}