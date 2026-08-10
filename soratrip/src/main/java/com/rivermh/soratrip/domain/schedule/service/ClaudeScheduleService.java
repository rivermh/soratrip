package com.rivermh.soratrip.domain.schedule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.ClaudeScheduleResponse;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleDayRepository;
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
public class ClaudeScheduleService {

    @Value("${openrouter.api.key}")
    private String apiKey;

    private final TravelScheduleRepository travelScheduleRepository;
    private final ScheduleDayRepository scheduleDayRepository;
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
        ClaudeScheduleResponse aiResponse = callOpenRouterApi(prompt, request);

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
            for (ClaudeScheduleResponse.DayDto dayDto : aiResponse.getDays()) {
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
                    for (ClaudeScheduleResponse.ItemDto itemDto : dayDto.getItems()) {
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
                                .recommendReason(itemDto.getRecommendReason())
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
     * 특정 하루만 AI로 다시 생성한다. 기존 dayNumber/visitDate는 그대로 두고, 그 날의 장소(items)만 교체한다.
     */
    public void regenerateDay(Long dayId, String extraPrompt, String email) {
        ScheduleDay day = scheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        TravelSchedule schedule = day.getTravelSchedule();
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("해당 일정을 수정할 권한이 없습니다.");
        }

        String prompt = buildDayRegeneratePrompt(schedule, day, extraPrompt);
        ClaudeScheduleResponse.DayDto dayDto = callOpenRouterApiForDay(prompt);

        day.getItems().clear();
        if (dayDto.getItems() != null) {
            for (ClaudeScheduleResponse.ItemDto itemDto : dayDto.getItems()) {
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
                        .recommendReason(itemDto.getRecommendReason())
                        .build();

                day.addItem(item);
            }
        }
    }

    private String buildDayRegeneratePrompt(TravelSchedule schedule, ScheduleDay day, String extraPrompt) {
        String regionName = schedule.getRegion() != null ? schedule.getRegion().getDisplayName() : "일본 주요 도시";

        String tagNames = "None";
        if (schedule.getTags() != null && !schedule.getTags().isEmpty()) {
            tagNames = schedule.getTags().stream()
                    .map(ScheduleTag::getDisplayName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("None");
        }

        String currentPlaces = day.getItems().stream()
                .map(ScheduleItem::getPlaceName)
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");

        return String.format("""
                You are an expert Japan travel planner focusing on accessibility and luggage-friendly routing.
                The user wants to REGENERATE just ONE day (day %d) of an existing %s itinerary. Do not return other days.

                Context:
                - Travel Region: %s
                - Travel Style/Tags: %s
                - Current plan for this day (to be fully replaced): %s
                - User's request for this day: %s

                STRICT JSON Format Requirement:
                Return ONLY valid JSON for this single day (no markdown, no comments). Use this EXACT structure:
                {
                  "items": [
                    {
                      "placeName": "Place Name",
                      "placeAddress": "Address",
                      "latitude": 35.6762,
                      "longitude": 139.7674,
                      "visitTime": "09:00",
                      "visitOrder": 1,
                      "memo": "Memo text",
                      "recommendReason": "One short sentence on why this place fits the requested tags/preferences"
                    }
                  ]
                }

                Rules:
                - All string values MUST have double quotes
                - All keys MUST have double quotes
                - latitude and longitude are numbers (no quotes)
                - visitOrder is a number (no quotes), starting from 1
                - Do NOT include markdown backticks
                - Do NOT include any text outside the JSON object
                - Write 'placeName', 'placeAddress', 'memo', and 'recommendReason' in Korean or Japanese, matching the current plan's language (default to Korean if unclear).
                - 'recommendReason' MUST be a single concrete sentence explaining why THIS place fits the Travel Style/Tags or the user's request for this day. If accessibility/luggage/stroller/senior tags were requested, prioritize mentioning the specific accessibility feature. Never leave it generic praise like "인기 명소예요".

                Make sure latitude and longitude coordinates are accurate real-world values for the requested location.
                """,
                day.getDayNumber(), regionName, regionName, tagNames, currentPlaces,
                extraPrompt != null && !extraPrompt.isBlank() ? extraPrompt : "Just make this day more interesting."
        );
    }

    /**
     * 하루치 재생성 전용 OpenRouter 호출 (재시도 로직 포함). 실패 시 fallback 없이 예외를 던져
     * 기존 day의 items를 건드리지 않고 그대로 유지한다 (호출부에서 items.clear() 하기 전에 실패해야 함).
     */
    private ClaudeScheduleResponse.DayDto callOpenRouterApiForDay(String promptText) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("HTTP-Referer", "https://soratrip.com");
                headers.set("X-Title", "Soratrip");

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", "anthropic/claude-3-haiku");
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 2048);

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system",
                        "content", "You are an expert JSON generator. You must output ONLY valid raw JSON without markdown tags. Every string value and key must be properly enclosed in double quotes."));
                messages.add(Map.of("role", "user", "content", promptText));
                requestBody.put("messages", messages);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                JsonNode rootNode = objectMapper.readTree(response.getBody());

                String jsonText = rootNode.path("choices").get(0)
                        .path("message").path("content").asText();

                String cleanedJson = cleanJsonResponse(jsonText);

                try {
                    ClaudeScheduleResponse.DayDto result = objectMapper.readValue(cleanedJson, ClaudeScheduleResponse.DayDto.class);
                    log.info("✅ OpenRouter 하루 재생성 API 호출 성공! (시도: {})", attempt + 1);
                    return result;
                } catch (Exception jsonParseException) {
                    log.warn("⚠️ 하루 재생성 JSON 파싱 실패 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        throw jsonParseException;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ 하루 재생성 API 호출 실패 (시도: {}/{}): {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new IllegalStateException("AI 일정 재생성에 실패했습니다. 잠시 후 다시 시도해주세요.", e);
                }
            }
        }

        throw new IllegalStateException("AI 일정 재생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * OpenRouter API 호출 (재시도 로직 포함)
     * https://openrouter.ai/docs
     * 무료 크레딧: $5
     */
    private ClaudeScheduleResponse callOpenRouterApi(String promptText, AiScheduleRequest request) {
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
                        "content", "You are an expert JSON generator. You must output ONLY valid raw JSON without markdown tags. Every string value and key must be properly enclosed in double quotes. CRITICAL: Automatically detect whether Korean or Japanese is requested or appropriate based on the input context, and write all content (title, placeName, placeAddress, memo, recommendReason) in that matching language (either KOREAN or JAPANESE)."));
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
                    ClaudeScheduleResponse result = objectMapper.readValue(cleanedJson, ClaudeScheduleResponse.class);
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

    private ClaudeScheduleResponse createFallbackSchedule(AiScheduleRequest request) {
        ClaudeScheduleResponse fallback = new ClaudeScheduleResponse();
        String regionName = request.getRegion() != null ? request.getRegion().getDisplayName() : "도쿄";
        fallback.setTitle("✨ [추천] " + regionName + " 힐링 인기 코스");

        List<ClaudeScheduleResponse.DayDto> days = new ArrayList<>();
        int daysCount = request.getDaysCount() > 0 ? request.getDaysCount() : 2;

        for (int i = 1; i <= daysCount; i++) {
            ClaudeScheduleResponse.DayDto day = new ClaudeScheduleResponse.DayDto();
            day.setDayNumber(i);

            List<ClaudeScheduleResponse.ItemDto> items = new ArrayList<>();

            if (i == 1) {
                ClaudeScheduleResponse.ItemDto item1 = new ClaudeScheduleResponse.ItemDto();
                item1.setPlaceName("시부야 스카이 (SHIBUYA SKY)");
                item1.setPlaceAddress("2 Chome-24-12 Shibuya, Shibuya City, Tokyo");
                item1.setLatitude(35.6585);
                item1.setLongitude(139.7013);
                item1.setVisitTime("10:00");
                item1.setVisitOrder(1);
                item1.setMemo("엘리베이터 이용 편리, 짐 보관소(코인라커) 4층 위치");
                items.add(item1);

                ClaudeScheduleResponse.ItemDto item2 = new ClaudeScheduleResponse.ItemDto();
                item2.setPlaceName("도쿄 타워 (Tokyo Tower)");
                item2.setPlaceAddress("4 Chome-2-8 Shibakoen, Minato City, Tokyo");
                item2.setLatitude(35.6586);
                item2.setLongitude(139.7454);
                item2.setVisitTime("15:00");
                item2.setVisitOrder(2);
                item2.setMemo("야경 명소, 메인 데크 진입 시 완만한 경사로 및 엘리베이터 완비");
                items.add(item2);
            } else {
                ClaudeScheduleResponse.ItemDto item1 = new ClaudeScheduleResponse.ItemDto();
                item1.setPlaceName("아사쿠사 센소지 (Senso-ji)");
                item1.setPlaceAddress("2 Chome-3-1 Asakusa, Taito City, Tokyo");
                item1.setLatitude(35.7148);
                item1.setLongitude(139.7967);
                item1.setVisitTime("11:00");
                item1.setVisitOrder(1);
                item1.setMemo("전통 거리 구경, 인근 평지 위주 동선으로 도보 이동 수월");
                items.add(item1);

                ClaudeScheduleResponse.ItemDto item2 = new ClaudeScheduleResponse.ItemDto();
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
                          "memo": "Memo text",
                          "recommendReason": "One short sentence on why this place fits the requested tags/companion/preferences"
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
                - Write 'title', 'placeName', 'placeAddress', 'memo', and 'recommendReason' in the detected language (Korean or Japanese).
                - 'recommendReason' MUST be a single concrete sentence explaining why THIS place fits the given Travel Style/Tags, Companion Type, or Additional Preferences (e.g. "1층 매장이라 계단 이동이 없어요", "역과 엘리베이터로 바로 연결돼요"). If accessibility/luggage/stroller/senior tags were requested, prioritize mentioning the specific accessibility feature. Never leave it generic praise like "인기 명소예요".
                
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