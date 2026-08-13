package com.rivermh.soratrip.domain.schedule.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TranslationCacheRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AI가 생성한 일정 텍스트(제목/장소명/메모/추천이유)를 조회 시점 로케일로 자동 번역한다.
// 한국어(기본 생성 언어)로 볼 때는 API 호출 없이 원문 그대로 두고, 그 외 언어로 볼 때만
// 번역이 필요한 항목을 모아 한 번의 API 호출로 일괄 번역한 뒤 DB에 캐싱해 다음부터는 재사용한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    private static final Map<String, String> TARGET_LANG_NAME = Map.of("ja", "Japanese");

    private final TranslationCacheRepository translationCacheRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Transactional
    public Map<String, String> translateScheduleForView(TravelSchedule schedule, Locale locale) {
        String lang = locale.getLanguage();
        if (!TARGET_LANG_NAME.containsKey(lang)) {
            return Collections.emptyMap();
        }

        List<Target> targets = collectTargets(schedule);
        if (targets.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> cacheByKey = loadExistingCache(schedule.getId(), targets, lang);

        List<Target> missing = targets.stream()
                .filter(t -> !cacheByKey.containsKey(cacheKey(t)))
                .toList();

        if (!missing.isEmpty()) {
            Map<Integer, String> translated = translateBatch(
                    missing.stream().map(Target::text).toList(), lang);

            for (int i = 0; i < missing.size(); i++) {
                String text = translated.get(i);
                if (!StringUtils.hasText(text)) {
                    continue; // 번역 실패 항목은 캐싱하지 않고 원문 폴백으로 처리
                }
                Target t = missing.get(i);
                cacheByKey.put(cacheKey(t), text);
                // save() 대신 INSERT IGNORE: 같은 일정을 동시에 여러 요청이 처음 번역하는 경우
                // 유니크 제약 충돌로 500 에러가 나던 걸 막는다 (다른 요청이 먼저 캐싱했으면 조용히 무시).
                translationCacheRepository.insertIgnoringDuplicate(
                        t.entityType(), t.entityId(), t.fieldName(), lang, text, LocalDateTime.now());
            }
        }

        Map<String, String> result = new HashMap<>();
        for (Target t : targets) {
            String translated = cacheByKey.get(cacheKey(t));
            result.put(t.viewKey(), translated != null ? translated : t.text());
        }
        return result;
    }

    private List<Target> collectTargets(TravelSchedule schedule) {
        List<Target> targets = new ArrayList<>();
        if (StringUtils.hasText(schedule.getTitle())) {
            targets.add(new Target("SCHEDULE", schedule.getId(), "title", schedule.getTitle(), "title"));
        }
        for (ScheduleDay day : schedule.getDays()) {
            for (ScheduleItem item : day.getItems()) {
                if (StringUtils.hasText(item.getPlaceName())) {
                    targets.add(new Target("SCHEDULE_ITEM", item.getId(), "placeName", item.getPlaceName(),
                            "item_" + item.getId() + "_placeName"));
                }
                if (StringUtils.hasText(item.getMemo())) {
                    targets.add(new Target("SCHEDULE_ITEM", item.getId(), "memo", item.getMemo(),
                            "item_" + item.getId() + "_memo"));
                }
                if (StringUtils.hasText(item.getRecommendReason())) {
                    targets.add(new Target("SCHEDULE_ITEM", item.getId(), "recommendReason", item.getRecommendReason(),
                            "item_" + item.getId() + "_recommendReason"));
                }
            }
        }
        return targets;
    }

    private Map<String, String> loadExistingCache(Long scheduleId, List<Target> targets, String lang) {
        Map<String, String> cacheByKey = new HashMap<>();

        translationCacheRepository.findByEntityTypeAndEntityIdAndLang("SCHEDULE", scheduleId, lang)
                .forEach(tc -> cacheByKey.put(cacheKey(tc.getEntityType(), tc.getEntityId(), tc.getFieldName()), tc.getTranslatedText()));

        List<Long> itemIds = targets.stream()
                .filter(t -> t.entityType().equals("SCHEDULE_ITEM"))
                .map(Target::entityId)
                .distinct()
                .toList();
        if (!itemIds.isEmpty()) {
            translationCacheRepository.findByEntityTypeAndEntityIdInAndLang("SCHEDULE_ITEM", itemIds, lang)
                    .forEach(tc -> cacheByKey.put(cacheKey(tc.getEntityType(), tc.getEntityId(), tc.getFieldName()), tc.getTranslatedText()));
        }

        return cacheByKey;
    }

    // 캐시에 없는 텍스트들을 번호를 매겨 한 번의 API 호출로 일괄 번역한다.
    // 실패 시(네트워크 오류, 파싱 실패 등) 빈 맵을 반환해 호출부가 원문으로 폴백하게 한다.
    private Map<Integer, String> translateBatch(List<String> texts, String targetLang) {
        String url = "https://openrouter.ai/api/v1/chat/completions";
        String targetLangName = TARGET_LANG_NAME.get(targetLang);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer", "https://soratrip.com");
            headers.set("X-Title", "Soratrip");

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("다음은 번호가 매겨진 여행 일정 텍스트 목록이다. 각 항목을 ").append(targetLangName)
                    .append("(으)로 자연스럽게 번역하라. 장소 이름은 실제 통용되는 현지 표기를 우선한다. ")
                    .append("설명이나 부연 없이, 원본과 정확히 같은 개수와 순서로 번역 결과만 담아라. ")
                    .append("반드시 아래 JSON 형식으로만 응답하라: {\"translations\": [\"...\", \"...\"]}\n\n");
            for (int i = 0; i < texts.size(); i++) {
                promptBuilder.append(i + 1).append(". ").append(texts.get(i)).append("\n");
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "google/gemini-3.5-flash-lite");
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 4096);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system",
                    "content", "You are an expert JSON generator and professional translator. Output ONLY valid raw JSON without markdown tags."));
            messages.add(Map.of("role", "user", "content", promptBuilder.toString()));
            requestBody.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String jsonText = rootNode.path("choices").get(0).path("message").path("content").asText();

            JsonNode parsed = objectMapper.readTree(cleanJsonResponse(jsonText));
            JsonNode translationsNode = parsed.path("translations");

            Map<Integer, String> result = new HashMap<>();
            if (translationsNode.isArray()) {
                for (int i = 0; i < translationsNode.size() && i < texts.size(); i++) {
                    result.put(i, translationsNode.get(i).asText());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("⚠️ 일정 번역 API 호출 실패: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String cleanJsonResponse(String jsonText) {
        jsonText = jsonText.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .replaceAll("```", "")
                .trim();

        int startIdx = jsonText.indexOf('{');
        int endIdx = jsonText.lastIndexOf('}');
        if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
            jsonText = jsonText.substring(startIdx, endIdx + 1);
        }
        return jsonText.trim();
    }

    private String cacheKey(Target t) {
        return cacheKey(t.entityType(), t.entityId(), t.fieldName());
    }

    private String cacheKey(String entityType, Long entityId, String fieldName) {
        return entityType + ":" + entityId + ":" + fieldName;
    }

    private record Target(String entityType, Long entityId, String fieldName, String text, String viewKey) {
    }
}
