package com.rivermh.soratrip.domain.schedule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.GeminiScheduleResponse;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroqScheduleService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final int MAX_RETRIES = 2;

    public Long createScheduleWithAi(AiScheduleRequest request, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 1. OpenRouter API 프롬프트 구성
        String prompt = buildPrompt(request);

        // 2. OpenRouter API 호출 (실패 시 Fallback 더미 데이터 반환)
        GeminiScheduleResponse aiResponse = callOpenRouterApi(prompt, request);

        // 3. 응답받은 데이터를 DB 엔티티로 변환 및 저장
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(request.getDaysCount() - 1);

        TravelSchedule schedule = TravelSchedule.builder()
                .member(member)
                .title(aiResponse.getTitle() != null ? aiResponse.getTitle() : "🤖 AI 추천 " + request.getRegion().getDisplayName() + " 여행")
                .region(request.getRegion())
                .startDate(startDate)
                .endDate(endDate)
                .isPublic(true)
                .tags(request.getTags() != null ? request.getTags() : new HashSet<>())
                .build();

        if (aiResponse.getDays() != null) {
            // AI 응답의 dayNumber는 검증되지 않은 값이므로, null/범위 밖/중복은 걸러내고 진행한다
            // (그대로 쓰면 NPE가 나거나 schedule의 startDate~endDate 범위를 벗어난 day가 조용히 생길 수 있음)
            Set<Integer> seenDayNumbers = new HashSet<>();
            for (GeminiScheduleResponse.DayDto dayDto : aiResponse.getDays()) {
                Integer dayNumber = dayDto.getDayNumber();
                if (dayNumber == null || dayNumber < 1 || dayNumber > request.getDaysCount()) {
                    log.warn("⚠️ AI 응답의 dayNumber가 유효하지 않아 건너뜁니다: {}", dayNumber);
                    continue;
                }
                if (!seenDayNumbers.add(dayNumber)) {
                    log.warn("⚠️ AI 응답에 중복된 dayNumber({})가 있어 건너뜁니다.", dayNumber);
                    continue;
                }

                ScheduleDay day = ScheduleDay.builder()
                        .travelSchedule(schedule)
                        .dayNumber(dayNumber)
                        .visitDate(startDate.plusDays(dayNumber - 1))
                        .build();

                if (dayDto.getItems() != null) {
                    for (GeminiScheduleResponse.ItemDto itemDto : dayDto.getItems()) {
                        LocalTime time = null;
                        if (itemDto.getVisitTime() != null && itemDto.getVisitTime().matches("\\d{2}:\\d{2}")) {
                            time = LocalTime.parse(itemDto.getVisitTime());
                        }

                        ScheduleItem item = ScheduleItem.builder()
                                .scheduleDay(day)
                                .placeName(itemDto.getPlaceName())
                                .placeAddress(itemDto.getPlaceAddress())
                                .latitude(itemDto.getLatitude())
                                .longitude(itemDto.getLongitude())
                                .visitOrder(itemDto.getVisitOrder())
                                .visitTime(time)
                                .memo(itemDto.getMemo())
                                .build();

                        day.addItem(item);
                    }
                }
                schedule.getDays().add(day);
            }
        }

        TravelSchedule saved = travelScheduleRepository.save(schedule);
        return saved.getId();
    }

    /**
     * OpenRouter API 호출 (재시도 로직 포함)
     * https://openrouter.ai/docs
     * 무료 크레딧: $5
     */
    private GeminiScheduleResponse callOpenRouterApi(String promptText, AiScheduleRequest request) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("HTTP-Referer", "https://soratrip.com");  // OpenRouter 요구사항
                headers.set("X-Title", "Soratrip");  // OpenRouter 요구사항

                Map<String, Object> requestBody = new HashMap<>();
                
                // 모델 선택 (원하는 모델로 변경 가능)
                // - meta-llama/llama-2-7b-chat (무료)
                // - mistralai/mistral-7b-instruct (무료)
                // - openai/gpt-4-turbo (유료)
                // - anthropic/claude-3-haiku (유료)
                requestBody.put("model", "anthropic/claude-3-haiku");
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 4096);

                List<Map<String, String>> messages = new ArrayList<>();
                // 한국어/일본어 자동 감지 및 출력 제약 추가된 시스템 프롬프트
                messages.add(Map.of("role", "system", 
                        "content", "You are an expert JSON generator. You must output ONLY valid raw JSON without markdown tags. Every string value and key must be properly enclosed in double quotes. CRITICAL: Automatically detect whether Korean or Japanese is requested or appropriate based on the input context, and write all content (title, placeName, placeAddress, memo) in that matching language (either KOREAN or JAPANESE)."));
                messages.add(Map.of("role", "user", "content", promptText));
                requestBody.put("messages", messages);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                JsonNode rootNode = objectMapper.readTree(response.getBody());

                String jsonText = rootNode.path("choices").get(0)
                        .path("message").path("content").asText();

                // JSON 응답 정제
                String cleanedJson = cleanJsonResponse(jsonText);

                // JSON 유효성 검증 및 파싱
                try {
                    GeminiScheduleResponse result = objectMapper.readValue(cleanedJson, GeminiScheduleResponse.class);
                    log.info("✅ OpenRouter API 호출 성공! (시도: {})", attempt + 1);
                    log.debug("응답: {}", cleanedJson.substring(0, Math.min(200, cleanedJson.length())));
                    return result;
                } catch (Exception jsonParseException) {
                    log.warn("⚠️ JSON 파싱 실패 (시도: {}). 재시도 중...", attempt + 1);
                    log.warn("원본: {}", jsonText.substring(0, Math.min(200, jsonText.length())));
                    log.warn("정제: {}", cleanedJson.substring(0, Math.min(200, cleanedJson.length())));

                    if (attempt == MAX_RETRIES) {
                        throw jsonParseException;
                    }
                }

            } catch (Exception e) {
                log.warn("⚠️ OpenRouter API 호출 실패 (시도: {}/{}): {}", 
                        attempt + 1, MAX_RETRIES + 1, e.getMessage());

                if (attempt == MAX_RETRIES) {
                    log.error("❌ 모든 재시도 실패. Fallback 일정 생성...", e);
                    return createFallbackSchedule(request);
                }
            }
        }

        return createFallbackSchedule(request);
    }

    /**
     * API 응답을 정제하는 메서드
     */
    private String cleanJsonResponse(String jsonText) {
        // 마크다운 코드블록 제거
        jsonText = jsonText.replaceAll("```json\\s*", "")
                          .replaceAll("```\\s*", "")
                          .replaceAll("```", "")
                          .trim();

        // JSON 영역만 추출
        int startIdx = jsonText.indexOf('{');
        int endIdx = jsonText.lastIndexOf('}');

        if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
            jsonText = jsonText.substring(startIdx, endIdx + 1);
        }

        return jsonText.trim();
    }

    private GeminiScheduleResponse createFallbackSchedule(AiScheduleRequest request) {
        GeminiScheduleResponse fallback = new GeminiScheduleResponse();
        String regionName = request.getRegion() != null ? request.getRegion().getDisplayName() : "도쿄";
        fallback.setTitle("✨ [추천] " + regionName + " 힐링 인기 코스");

        List<GeminiScheduleResponse.DayDto> days = new ArrayList<>();
        int daysCount = request.getDaysCount() > 0 ? request.getDaysCount() : 2;

        for (int i = 1; i <= daysCount; i++) {
            GeminiScheduleResponse.DayDto day = new GeminiScheduleResponse.DayDto();
            day.setDayNumber(i);

            List<GeminiScheduleResponse.ItemDto> items = new ArrayList<>();

            if (i == 1) {
                GeminiScheduleResponse.ItemDto item1 = new GeminiScheduleResponse.ItemDto();
                item1.setPlaceName("시부야 스카이 (SHIBUYA SKY)");
                item1.setPlaceAddress("2 Chome-24-12 Shibuya, Shibuya City, Tokyo");
                item1.setLatitude(35.6585);
                item1.setLongitude(139.7013);
                item1.setVisitTime("10:00");
                item1.setVisitOrder(1);
                item1.setMemo("엘리베이터 이용 편리, 짐 보관소(코인라커) 4층 위치");
                items.add(item1);

                GeminiScheduleResponse.ItemDto item2 = new GeminiScheduleResponse.ItemDto();
                item2.setPlaceName("도쿄 타워 (Tokyo Tower)");
                item2.setPlaceAddress("4 Chome-2-8 Shibakoen, Minato City, Tokyo");
                item2.setLatitude(35.6586);
                item2.setLongitude(139.7454);
                item2.setVisitTime("15:00");
                item2.setVisitOrder(2);
                item2.setMemo("야경 명소, 메인 데크 진입 시 완만한 경사로 및 엘리베이터 완비");
                items.add(item2);
            } else {
                GeminiScheduleResponse.ItemDto item1 = new GeminiScheduleResponse.ItemDto();
                item1.setPlaceName("아사쿠사 센소지 (Senso-ji)");
                item1.setPlaceAddress("2 Chome-3-1 Asakusa, Taito City, Tokyo");
                item1.setLatitude(35.7148);
                item1.setLongitude(139.7967);
                item1.setVisitTime("11:00");
                item1.setVisitOrder(1);
                item1.setMemo("전통 거리 구경, 인근 평지 위주 동선으로 도보 이동 수월");
                items.add(item1);

                GeminiScheduleResponse.ItemDto item2 = new GeminiScheduleResponse.ItemDto();
                item2.setPlaceName("우에노 온시 공원 (Ueno Park)");
                item2.setPlaceAddress("Uenokoen, Taito City, Tokyo");
                item2.setLatitude(35.7140);
                item2.setLongitude(139.7741);
                item2.setVisitTime("14:30");
                item2.setVisitOrder(2);
                item2.setMemo("공원 내 산책로 정비 잘 됨, 카페 및 휴게 공간 다수 보유");
                items.add(item2);
            }

            day.setItems(items);
            days.add(day);
        }

        fallback.setDays(days);
        return fallback;
    }

    private String buildPrompt(AiScheduleRequest req) {
        String regionName = (req.getRegion() != null) ? req.getRegion().getDisplayName() : "일본 주요 도시";

        String tagNames = "None";
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            tagNames = req.getTags().stream()
                    .map(ScheduleTag::getDisplayName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("None");
        }

        return String.format("""
                You are an expert Japan travel planner focusing on accessibility and luggage-friendly routing.
                Create a detailed %d-day itinerary for %s, Japan.
                
                Constraints & Context:
                - Travel Region: %s
                - Companion Type: %s
                - Travel Style/Tags: %s
                - Additional Preferences & Requests: %s
                
                Important Guidelines:
                1. Suggest unique, highly relevant places in %s according to the companion type and preferences.
                2. Reflect the 'Additional Preferences & Requests' strictly (e.g. stairs, walking distance, luggage, specific spot requests).
                3. Automatically detect whether Korean or Japanese is requested or appropriate based on the input context, and write all content in that matching language.
                
                STRICT JSON Format Requirement:
                Return ONLY valid JSON (no markdown, no comments). Use this EXACT structure:
                {
                  "title": "Itinerary Title",
                  "days": [
                    {
                      "dayNumber": 1,
                      "items": [
                        {
                          "placeName": "Place Name",
                          "placeAddress": "Address",
                          "latitude": 35.6762,
                          "longitude": 139.7674,
                          "visitTime": "09:00",
                          "visitOrder": 1,
                          "memo": "Memo text"
                        }
                      ]
                    }
                  ]
                }
                
                Rules:
                - All string values MUST have double quotes
                - All keys MUST have double quotes
                - latitude and longitude are numbers (no quotes)
                - visitOrder is a number (no quotes)
                - Do NOT include markdown backticks
                - Do NOT include any text outside the JSON object
                - Write 'title', 'placeName', 'placeAddress', and 'memo' in the detected language (Korean or Japanese).
                
                Make sure latitude and longitude coordinates are accurate real-world values for the requested location.
                """,
                req.getDaysCount(),
                regionName,
                regionName,
                req.getCompanionType() != null && !req.getCompanionType().isBlank() ? req.getCompanionType() : "General",
                tagNames,
                req.getExtraPrompt() != null && !req.getExtraPrompt().isBlank() ? req.getExtraPrompt() : "None",
                regionName
        );
    }
}