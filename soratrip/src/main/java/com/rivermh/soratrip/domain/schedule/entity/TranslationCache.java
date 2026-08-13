package com.rivermh.soratrip.domain.schedule.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI가 생성한 일정 텍스트(제목/장소명/메모/추천이유 등)를 다른 언어로 조회할 때 쓰는 번역 캐시.
// 언어당 한 번만 번역 API를 호출하고, 이후 조회는 여기서 재사용한다.
@Entity
@Table(name = "translation_cache", uniqueConstraints = @UniqueConstraint(
        columnNames = {"entity_type", "entity_id", "field_name", "lang"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType; // "SCHEDULE" or "SCHEDULE_ITEM"

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "field_name", nullable = false, length = 30)
    private String fieldName; // "title" / "placeName" / "memo" / "recommendReason"

    @Column(nullable = false, length = 10)
    private String lang; // "ja"

    @Column(name = "translated_text", columnDefinition = "TEXT", nullable = false)
    private String translatedText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
