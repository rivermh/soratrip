package com.rivermh.soratrip.domain.schedule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.ClaudeScheduleResponse;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.PlaceType;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleDayRepository;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final int MAX_RETRIES = 2;

    // AI 일정 생성/재생성은 매 호출마다 유료 API 비용이 발생하므로, 회원당 시간당 호출 횟수를 제한한다
    // (MailService.checkPasswordResetRequestRate와 동일한 스타일)
    private static final String AI_GEN_PREFIX = "ai-gen:";
    private static final int AI_GEN_THRESHOLD = 10;
    private static final long AI_GEN_WINDOW_SECONDS = 3600L; // 1시간

    private void checkAiGenerationRate(String email) {
        String key = AI_GEN_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(AI_GEN_WINDOW_SECONDS));
        }
        if (count != null && count > AI_GEN_THRESHOLD) {
            throw new IllegalStateException("AI 일정 생성 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    // AI 응답의 placeType은 검증되지 않은 문자열이므로, enum에 없는 값/null은 조용히 null로 흡수한다
    private PlaceType parsePlaceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PlaceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ AI 응답의 placeType이 유효하지 않아 무시합니다: {}", raw);
            return null;
        }
    }

    public Long createScheduleWithAi(AiScheduleRequest request, String email) {
        checkAiGenerationRate(email);

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
                                .placeType(parsePlaceType(itemDto.getPlaceType()))
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
        checkAiGenerationRate(email);

        ScheduleDay day = scheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        TravelSchedule schedule = day.getTravelSchedule();
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("해당 일정을 수정할 권한이 없습니다.");
        }

        String prompt = buildDayRegeneratePrompt(schedule, day, extraPrompt);
        ClaudeScheduleResponse.DayDto dayDto = callOpenRouterApiForDay(prompt, schedule.getRegion());

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
                        .placeType(parsePlaceType(itemDto.getPlaceType()))
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

        // 같은 여행의 다른 날에 이미 나온 장소는 재생성 시 다시 제안하지 않도록 제외 목록으로 전달한다
        String otherDaysPlaces = schedule.getDays().stream()
                .filter(d -> !d.getId().equals(day.getId()))
                .flatMap(d -> d.getItems().stream())
                .map(ScheduleItem::getPlaceName)
                .filter(Objects::nonNull)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("None");

        return String.format("""
                You are a local food curator who has lived in %s for over a decade and knows its
                back alleys, markets, and family-run shops -- not a generic tour guide.
                The user wants to REGENERATE just ONE day (day %d) of an existing %s itinerary,
                built around storied local eateries rather than famous landmarks or chain
                restaurants. Do not return other days.

                Context:
                - Travel Region: %s
                - Travel Style/Tags: %s
                - Current plan for this day (to be fully replaced): %s
                - Places already used on OTHER days of this trip (do NOT repeat these): %s
                - User's request for this day: %s

                Important Guidelines:
                1. EXCLUDE chain restaurants and franchises entirely. For famous, iconic landmark
                   attractions, don't ban them outright, but keep them to a minimum -- at most ONE
                   for this day, and only if it genuinely fits the trip. Fill the rest with small,
                   generational, neighborhood places.
                2. At least half of the places suggested for this day should be food-related spots
                   chosen for their story, not their fame.
                3. The user's request for this day (below) is a MANDATORY constraint, not optional
                   flavor text. Make sure EVERY item you choose is compatible with it, even if an
                   otherwise-fitting local place would conflict with it.

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
                      "recommendReason": "2-3 sentences telling this place's story",
                      "placeType": "RESTAURANT"
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
                - 'placeType' MUST be exactly one of: ATTRACTION, RESTAURANT, CAFE, LODGING,
                  TRANSPORT, SHOPPING, ETC (uppercase, no other values). Pick the one that best
                  describes the place itself (e.g. a ramen shop or izakaya is RESTAURANT, a
                  standalone dessert/coffee spot is CAFE, a temple/park/viewpoint is ATTRACTION).
                - Write 'placeName', 'placeAddress', 'memo', and 'recommendReason' in Korean or Japanese, matching the current plan's language (default to Korean if unclear).
                - 'recommendReason' MUST be 2-3 concrete sentences that include AT LEAST TWO of
                  the following: the shop's history or why it opened, the story behind its
                  signature dish, its relationship to the neighborhood/market, an anecdote about
                  the owner or chef, or why locals (not tourists) go there. If accessibility,
                  luggage, stroller, or senior tags were requested, also weave in the specific
                  accessibility feature.
                - NEVER use generic praise or guidebook phrases such as "인기 명소예요", "유명한",
                  "필수 코스", "여행객이라면 꼭", "인스타 핫플", "관광지로 유명한", or their
                  English/Japanese equivalents.
                - Do NOT assert precise unverifiable facts (an exact founding year, "3rd
                  generation", a named award, a specific celebrity visit, etc.) as certain fact
                  unless you are reasonably confident they are true for this real place. When
                  unsure, describe plausible general characteristics instead (worn signage, a
                  handwritten menu, regulars who work nearby, a market-adjacent feel) rather than
                  inventing specific unverifiable claims.
                - Before returning the JSON, silently re-check every item against the rules above
                  and replace any item that fails.

                Make sure latitude and longitude coordinates are accurate real-world values for the requested location.
                """,
                regionName, day.getDayNumber(), regionName,
                regionName, tagNames, currentPlaces, otherDaysPlaces,
                extraPrompt != null && !extraPrompt.isBlank() ? extraPrompt : "Just make this day more interesting."
        );
    }

    /**
     * 하루치 재생성 전용 OpenRouter 호출 (재시도 로직 포함). 실패 시 fallback 없이 예외를 던져
     * 기존 day의 items를 건드리지 않고 그대로 유지한다 (호출부에서 items.clear() 하기 전에 실패해야 함).
     */
    private ClaudeScheduleResponse.DayDto callOpenRouterApiForDay(String promptText, Region region) {
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

                ClaudeScheduleResponse.DayDto result;
                try {
                    result = objectMapper.readValue(cleanedJson, ClaudeScheduleResponse.DayDto.class);
                } catch (Exception jsonParseException) {
                    log.warn("⚠️ 하루 재생성 JSON 파싱 실패 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        throw jsonParseException;
                    }
                    continue;
                }

                if (hasGenericItems(result.getItems())) {
                    log.warn("⚠️ 하루 재생성 응답에 서사 없는 추천 이유가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        throw new IllegalStateException("AI가 서사 있는 추천을 만들지 못했습니다. 잠시 후 다시 시도해주세요.");
                    }
                    continue;
                }

                if (hasInvalidCoordinates(result.getItems(), region)) {
                    log.warn("⚠️ 하루 재생성 응답에 지역을 벗어난 좌표가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        throw new IllegalStateException("AI가 정확한 위치 정보를 만들지 못했습니다. 잠시 후 다시 시도해주세요.");
                    }
                    continue;
                }

                log.info("✅ OpenRouter 하루 재생성 API 호출 성공! (시도: {})", attempt + 1);
                return result;
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
                ClaudeScheduleResponse result;
                try {
                    result = objectMapper.readValue(cleanedJson, ClaudeScheduleResponse.class);
                } catch (Exception jsonParseException) {
                    log.warn("⚠️ JSON 파싱 실패 (시도: {}). 재시도 중...", attempt + 1);
                    log.warn("원본: {}", jsonText.substring(0, Math.min(200, jsonText.length())));
                    log.warn("정제: {}", cleanedJson.substring(0, Math.min(200, cleanedJson.length())));

                    if (attempt == MAX_RETRIES) {
                        throw jsonParseException;
                    }
                    continue;
                }

                // 파싱은 됐지만 뻔한 관광지 홍보 문구 수준의 추천 이유가 섞여 있으면 재시도한다
                boolean hasGeneric = result.getDays() != null
                        && result.getDays().stream().anyMatch(day -> hasGenericItems(day.getItems()));
                if (hasGeneric) {
                    log.warn("⚠️ 서사 없는 추천 이유가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        log.error("❌ 재시도 후에도 서사 없는 추천이 남아있어 Fallback 일정으로 대체합니다.");
                        return createFallbackSchedule(request);
                    }
                    continue;
                }

                // 파싱/서사 검증은 통과했지만, 요청한 지역과 동떨어진 좌표가 섞여 있으면 지도가 엉뚱하게 찍히므로 재시도한다
                boolean hasBadCoords = result.getDays() != null
                        && result.getDays().stream().anyMatch(day -> hasInvalidCoordinates(day.getItems(), request.getRegion()));
                if (hasBadCoords) {
                    log.warn("⚠️ 요청 지역을 벗어난 좌표가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        log.error("❌ 재시도 후에도 좌표 오류가 남아있어 Fallback 일정으로 대체합니다.");
                        return createFallbackSchedule(request);
                    }
                    continue;
                }

                // 파싱/서사/좌표 검증은 통과했지만, 같은 장소가 여러 날에 걸쳐 중복되면 날짜별로
                // 똑같은 일정이 나온 것처럼 보이므로 재시도한다
                if (hasDuplicateAcrossDays(result.getDays())) {
                    log.warn("⚠️ 날짜 간 중복된 장소가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        log.error("❌ 재시도 후에도 날짜 간 중복 장소가 남아있어 Fallback 일정으로 대체합니다.");
                        return createFallbackSchedule(request);
                    }
                    continue;
                }

                log.info("✅ OpenRouter API 호출 성공! (시도: {})", attempt + 1);
                log.debug("응답: {}", cleanedJson.substring(0, Math.min(200, cleanedJson.length())));
                return result;

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

    // recommendReason에 이 표현이 섞여 있으면 서사 없는 뻔한 홍보 문구로 간주한다
    private static final List<String> GENERIC_PHRASES = List.of(
            "인기 명소", "유명한", "필수 코스", "여행객이라면", "인스타 핫플", "관광지로 유명",
            "must-visit", "must visit", "famous spot", "popular tourist"
    );

    /**
     * recommendReason이 서사 없이 뻔한 홍보 문구 수준에 그치는지 검사한다.
     * 비어있거나, 너무 짧거나, 금지 표현이 섞여 있으면 "서사 없음"으로 간주한다.
     */
    private boolean isGenericRecommendation(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() < 15) {
            return true;
        }
        String normalized = reason.toLowerCase();
        return GENERIC_PHRASES.stream().anyMatch(phrase -> normalized.contains(phrase.toLowerCase()));
    }

    private boolean hasGenericItems(List<ClaudeScheduleResponse.ItemDto> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().anyMatch(item -> isGenericRecommendation(item.getRecommendReason()));
    }

    // 지역별 대략적인 위도/경도 범위 { latMin, latMax, lonMin, lonMax }.
    // AI가 완전히 엉뚱한 지역 좌표를 지어내는 경우(지도 오표시)만 걸러내는 용도라, 인접 당일치기
    // 명소(가마쿠라/요코하마/교토/나라 등)까지 포용하도록 넉넉하게 잡는다.
    private static final Map<Region, double[]> REGION_BOUNDS = Map.of(
            Region.TOKYO, new double[]{34.9, 36.8, 138.5, 140.6},
            Region.OSAKA, new double[]{34.0, 35.3, 134.8, 136.2},
            Region.FUKUOKA, new double[]{32.5, 34.0, 129.5, 131.5},
            Region.HOKKAIDO, new double[]{41.3, 45.6, 139.3, 145.9},
            Region.SEOUL, new double[]{37.0, 38.0, 126.4, 127.5},
            Region.BUSAN, new double[]{34.8, 35.5, 128.7, 129.3},
            Region.JEJU, new double[]{33.1, 33.6, 126.1, 126.9}
    );

    /**
     * 응답에 좌표가 아예 없거나(null), 선택된 지역의 대략적인 범위를 벗어난 항목이 있는지 검사한다.
     * region이 매핑에 없으면(향후 지역 추가 등) 검증을 건너뛴다.
     */
    private boolean hasInvalidCoordinates(List<ClaudeScheduleResponse.ItemDto> items, Region region) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        double[] bounds = region != null ? REGION_BOUNDS.get(region) : null;
        if (bounds == null) {
            return false;
        }
        return items.stream().anyMatch(item -> {
            Double lat = item.getLatitude();
            Double lon = item.getLongitude();
            if (lat == null || lon == null) {
                return true;
            }
            return lat < bounds[0] || lat > bounds[1] || lon < bounds[2] || lon > bounds[3];
        });
    }

    /**
     * 전체 일정 생성 응답에서 같은 장소(placeName)가 여러 날에 걸쳐 중복되는지 검사한다.
     * 단발성 하루 재생성(regenerateDay)은 다른 날의 장소를 프롬프트에서 직접 제외 목록으로
     * 넘기므로 이 검사가 필요 없고, 전체 일정을 한 번에 받는 이 경로에서만 사용한다.
     */
    private boolean hasDuplicateAcrossDays(List<ClaudeScheduleResponse.DayDto> days) {
        if (days == null || days.size() < 2) {
            return false;
        }
        Set<String> seenPlaceNames = new HashSet<>();
        for (ClaudeScheduleResponse.DayDto day : days) {
            if (day.getItems() == null) {
                continue;
            }
            for (ClaudeScheduleResponse.ItemDto item : day.getItems()) {
                String placeName = item.getPlaceName();
                if (placeName == null || placeName.isBlank()) {
                    continue;
                }
                if (!seenPlaceNames.add(placeName.trim().toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
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
                item1.setPlaceName("츠키지 장외시장 다마고야키 노점");
                item1.setPlaceAddress("4 Chome-16-2 Tsukiji, Chuo City, Tokyo");
                item1.setLatitude(35.6654);
                item1.setLongitude(139.7707);
                item1.setVisitTime("09:00");
                item1.setVisitOrder(1);
                item1.setMemo("아침 일찍 갈수록 줄이 짧음, 평지 노점이라 이동 편함");
                item1.setRecommendReason("경매가 끝난 새벽 시장 상인들의 아침 끼니로 시작된 다마고야키 집으로, 3대째 같은 화로에서 한 겹씩 말아 굽는 방식을 고집한다. 관광객보다 근처 수산물 상인들이 출근길에 먼저 들르는 곳이다.");
                item1.setPlaceType("RESTAURANT");
                items.add(item1);

                ClaudeScheduleResponse.ItemDto item2 = new ClaudeScheduleResponse.ItemDto();
                item2.setPlaceName("몬자야키 골목 노포");
                item2.setPlaceAddress("3 Chome-12 Tsukishima, Chuo City, Tokyo");
                item2.setLatitude(35.6640);
                item2.setLongitude(139.7825);
                item2.setVisitTime("15:00");
                item2.setVisitOrder(2);
                item2.setMemo("몬자야키 골목 전체가 평지, 매장 입구 단차 거의 없음");
                item2.setRecommendReason("공장 노동자들의 저녁 끼니용으로 시작된 몬자야키 골목의 원조 격 가게로, 철판 앞에서 손님이 직접 구워 먹는 방식을 처음 정착시킨 곳 중 하나로 알려져 있다. 관광객보다 퇴근길 동네 회사원들이 더 많이 보인다.");
                item2.setPlaceType("RESTAURANT");
                items.add(item2);
            } else {
                ClaudeScheduleResponse.ItemDto item1 = new ClaudeScheduleResponse.ItemDto();
                item1.setPlaceName("야나카 긴자 멘치카츠 정육점");
                item1.setPlaceAddress("3 Chome-13-3 Yanaka, Taito City, Tokyo");
                item1.setLatitude(35.7280);
                item1.setLongitude(139.7669);
                item1.setVisitTime("11:00");
                item1.setVisitOrder(1);
                item1.setMemo("좁은 상점가 초입 평지 매장, 서서 먹거나 포장 가능");
                item1.setRecommendReason("원래는 동네 정육점이었는데, 남는 자투리 고기로 만든 멘치카츠가 입소문이 나며 지금은 골목 명물이 됐다. 튀김옷 배합은 3대째 손자에게만 구전으로 전해진다고 가게 앞에 적혀 있다.");
                item1.setPlaceType("RESTAURANT");
                items.add(item1);

                ClaudeScheduleResponse.ItemDto item2 = new ClaudeScheduleResponse.ItemDto();
                item2.setPlaceName("우에노 온시 공원 (Ueno Park)");
                item2.setPlaceAddress("Uenokoen, Taito City, Tokyo");
                item2.setLatitude(35.7140);
                item2.setLongitude(139.7741);
                item2.setVisitTime("14:30");
                item2.setVisitOrder(2);
                item2.setMemo("공원 내 산책로 정비 잘 됨, 카페 및 휴게 공간 다수 보유");
                item2.setRecommendReason("공원 자체보다 서쪽 출구 인근 벤치가 숨은 포인트인데, 근처 상점가 상인들이 점심시간마다 이곳에서 도시락을 먹어 '상인들의 마당'이라 불린다.");
                item2.setPlaceType("ATTRACTION");
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
                You are a local food curator who has lived in %s for over a decade and knows its
                back alleys, markets, and family-run shops -- not a generic tour guide.
                Create a detailed %d-day itinerary for %s, Japan, built around storied local
                eateries rather than famous landmarks or chain restaurants.

                Constraints & Context:
                - Travel Region: %s
                - Companion Type: %s
                - Travel Style/Tags: %s
                - Additional Preferences & Requests: %s

                Important Guidelines:
                1. EXCLUDE chain restaurants and franchises entirely. For famous, iconic landmark
                   attractions, don't ban them outright, but keep them to a minimum -- at most ONE
                   per day, and only if it genuinely fits the trip. Fill the rest of the itinerary
                   with small, generational, neighborhood places over anything a typical guidebook
                   would list first.
                2. At least half of the places suggested per day should be food-related spots
                   (a restaurant, izakaya, market stall, cafe, etc.) chosen for their story, not
                   their fame.
                3. The 'Additional Preferences & Requests' field is a MANDATORY constraint from the
                   user, not optional flavor text. Read it carefully and make sure EVERY item you
                   choose is compatible with it. If an item would conflict with that request (even
                   if it otherwise fits the local-food theme), do not include it.
                4. Automatically detect whether Korean or Japanese is requested or appropriate
                   based on the input context, and write all content in that matching language.
                5. EVERY day must be built around entirely different places from every other day
                   in this itinerary. Never reuse the same place -- even a great one -- across
                   multiple days. Before finalizing, check the full place list across all days
                   and replace any place that appears more than once.

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
                          "recommendReason": "2-3 sentences telling this place's story",
                          "placeType": "RESTAURANT"
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
                - 'placeType' MUST be exactly one of: ATTRACTION, RESTAURANT, CAFE, LODGING,
                  TRANSPORT, SHOPPING, ETC (uppercase, no other values). Pick the one that best
                  describes the place itself (e.g. a ramen shop or izakaya is RESTAURANT, a
                  standalone dessert/coffee spot is CAFE, a temple/park/viewpoint is ATTRACTION).
                - Write 'title', 'placeName', 'placeAddress', 'memo', and 'recommendReason' in the detected language (Korean or Japanese).
                - 'recommendReason' MUST be 2-3 concrete sentences that include AT LEAST TWO of
                  the following: the shop's history or why it opened, the story behind its
                  signature dish, its relationship to the neighborhood/market, an anecdote about
                  the owner or chef, or why locals (not tourists) go there. If accessibility,
                  luggage, stroller, or senior tags were requested, also weave in the specific
                  accessibility feature.
                - NEVER use generic praise or guidebook phrases such as "인기 명소예요", "유명한",
                  "필수 코스", "여행객이라면 꼭", "인스타 핫플", "관광지로 유명한", or their
                  English/Japanese equivalents (e.g. "must-visit", "famous spot", "popular tourist
                  attraction").
                - Bad example: "현지인에게 인기 많은 라멘 맛집입니다."
                  Good example: "3대째 이어온 라멘집으로, 창업자가 시장 상인들 아침 끼니용으로 팔던
                  국물이 지금의 시그니처가 됐다. 관광 거리에서 두 블록 떨어져 있어 여행객보다 근처
                  공장 직원들이 아침 7시부터 줄을 선다."
                - Do NOT assert precise unverifiable facts (an exact founding year, "3rd
                  generation", a named award, a specific celebrity visit, etc.) as certain fact
                  unless you are reasonably confident they are true for this real place. When
                  unsure, describe plausible general characteristics instead (worn signage, a
                  handwritten menu, regulars who work nearby, a market-adjacent feel) rather than
                  inventing specific unverifiable claims.
                - Before returning the JSON, silently re-check every item against the rules above
                  and replace any item that fails.

                Make sure latitude and longitude coordinates are accurate real-world values for the requested location.
                """,
                regionName,
                req.getDaysCount(),
                regionName,
                regionName,
                req.getCompanionType() != null && !req.getCompanionType().isBlank() ? req.getCompanionType() : "General",
                tagNames,
                req.getExtraPrompt() != null && !req.getExtraPrompt().isBlank() ? req.getExtraPrompt() : "None"
        );
    }
}