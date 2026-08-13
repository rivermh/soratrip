package com.rivermh.soratrip.domain.schedule.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.schedule.entity.TranslationCache;

public interface TranslationCacheRepository extends JpaRepository<TranslationCache, Long> {

    List<TranslationCache> findByEntityTypeAndEntityIdAndLang(String entityType, Long entityId, String lang);

    List<TranslationCache> findByEntityTypeAndEntityIdInAndLang(String entityType, List<Long> entityIds, String lang);

    // 같은 일정을 여러 탭/요청이 동시에 처음 번역할 때 (entity_type, entity_id, field_name, lang) 유니크 제약이
    // 충돌할 수 있어, save() 대신 INSERT IGNORE로 넣는다. 이미 다른 요청이 캐싱해뒀으면 그냥 조용히 무시된다.
    @Modifying
    @Query(value = "INSERT IGNORE INTO translation_cache " +
            "(entity_type, entity_id, field_name, lang, translated_text, created_at) " +
            "VALUES (:entityType, :entityId, :fieldName, :lang, :translatedText, :createdAt)", nativeQuery = true)
    void insertIgnoringDuplicate(@Param("entityType") String entityType, @Param("entityId") Long entityId,
            @Param("fieldName") String fieldName, @Param("lang") String lang,
            @Param("translatedText") String translatedText, @Param("createdAt") LocalDateTime createdAt);
}
