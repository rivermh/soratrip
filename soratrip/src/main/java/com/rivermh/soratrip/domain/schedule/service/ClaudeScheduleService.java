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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;

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

    // 하루씩 개별 생성(createScheduleDayByDay) 폴백에서 날짜별 요청을 동시에 보내기 위한 전용 풀.
    // daysCount가 최대 7(AiScheduleRequest.daysCount @Max(7))이라 크게 잡을 필요가 없다.
    private final ExecutorService dayGenerationExecutor = Executors.newFixedThreadPool(7);

    @PreDestroy
    public void shutdownDayGenerationExecutor() {
        dayGenerationExecutor.shutdown();
    }

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

        return buildDayPromptCore(regionName, tagNames, currentPlaces, otherDaysPlaces,
                "REGENERATE just ONE day (day %d) of an existing %s itinerary".formatted(day.getDayNumber(), regionName),
                extraPrompt != null && !extraPrompt.isBlank() ? extraPrompt : "Just make this day more interesting.");
    }

    /**
     * 전체 일정을 새로 만들 때, 개별 날짜 하나만 생성하기 위한 프롬프트를 만든다.
     * (한 번에 여러 날을 요청하면 저가형 모델이 날짜 간 중복을 잘 피하지 못해, 실패 시
     * 이미 확정된 다른 날의 장소를 제외 목록으로 넘기며 하루씩 개별 호출하는 용도)
     */
    private String buildNewDayPrompt(AiScheduleRequest request, int dayNumber, String otherDaysPlaces) {
        String regionName = (request.getRegion() != null) ? request.getRegion().getDisplayName() : "일본 주요 도시";

        String tagNames = "None";
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            tagNames = request.getTags().stream()
                    .map(ScheduleTag::getDisplayName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("None");
        }

        return buildDayPromptCore(regionName, tagNames, "None", otherDaysPlaces,
                "create day %d of a new %d-day %s itinerary".formatted(dayNumber, request.getDaysCount(), regionName),
                request.getExtraPrompt() != null && !request.getExtraPrompt().isBlank()
                        ? request.getExtraPrompt() : "None");
    }

    /**
     * buildDayRegeneratePrompt / buildNewDayPrompt가 공유하는 하루치 생성 프롬프트 본문.
     */
    private String buildDayPromptCore(String regionName, String tagNames, String currentPlaces,
                                        String otherDaysPlaces, String taskDescription, String userRequest) {
        return String.format("""
                You are a local food curator who has lived in %s for over a decade and knows its
                back alleys, markets, and family-run shops -- not a generic tour guide.
                The user wants you to %s,
                built around storied local eateries rather than famous landmarks or chain
                restaurants. Do not return other days.

                Context:
                - Travel Region: %s
                - Travel Style/Tags: %s
                - Current plan for this day (to be fully replaced, or "None" if this day is new): %s
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
                   otherwise-fitting local place would conflict with it. If the request implies a
                   tier or vibe (e.g., "luxury", "budget", "high-end", "가성비"), that tier applies
                   to the ENTIRE day, not just one anchor item like the hotel -- e.g., a "luxury
                   beachfront hotel" request means every meal/activity that day should also read
                   as upscale, never a cheap fast-food chain like 김밥천국/Gimbap Cheonguk sitting
                   next to a five-star hotel.

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
                regionName, taskDescription, regionName,
                tagNames, currentPlaces, otherDaysPlaces, userRequest
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
                requestBody.put("model", "google/gemini-3.5-flash-lite");
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 4096);

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

                if (hasDuplicateItemNames(result.getItems())) {
                    log.warn("⚠️ 하루 재생성 응답 내에 같은 장소가 중복 등장했습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        throw new IllegalStateException("AI가 중복 없는 장소 목록을 만들지 못했습니다. 잠시 후 다시 시도해주세요.");
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
                
                // claude-3-haiku는 2026-08 기준 OpenRouter 모델 목록에서 빠져 있고(사실상 단종),
                // max_tokens 한도도 4096으로 낮아 긴 일정에서 잘림 문제가 있었다. gemini-3.5-flash-lite는
                // 가격대는 비슷하면서 출력 한도가 65,536토큰이라 그 문제가 구조적으로 사라진다.
                requestBody.put("model", "google/gemini-3.5-flash-lite");
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 8192);

                List<Map<String, String>> messages = new ArrayList<>();
                // 한국어/일본어 자동 감지 및 출력 제약 추가된 시스템 프롬프트
                messages.add(Map.of("role", "system", 
                        "content", "You are an expert JSON generator. You must output ONLY valid raw JSON without markdown tags. Every string value and key must be properly enclosed in double quotes. CRITICAL: Automatically detect whether Korean or Japanese is requested or appropriate based on the input context, and write all content (title, placeName, placeAddress, memo, recommendReason) in that matching language (either KOREAN or JAPANESE)."));
                messages.add(Map.of("role", "user", "content", promptText));
                requestBody.put("messages", messages);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                JsonNode rootNode = objectMapper.readTree(response.getBody());

                // finish_reason이 "length"면 max_tokens 한도에 걸려 응답이 중간에 잘렸다는 뜻이다.
                // gemini-3.5-flash-lite로 바꾸면서 한도를 8192로 넉넉하게 잡아 거의 안 일어나지만,
                // 그래도 걸리면 같은 일수/프롬프트라 재시도해도 또 잘릴 뿐이므로, 재시도를 낭비하지 말고
                // 바로 하루씩 개별 생성(병렬)으로 넘어간다.
                String finishReason = rootNode.path("choices").get(0).path("finish_reason").asText("");
                if ("length".equals(finishReason)) {
                    log.warn("⚠️ 응답이 max_tokens 한도에 걸려 잘렸습니다 (시도: {}). 재시도 없이 하루씩 개별 생성으로 전환합니다.", attempt + 1);
                    return createScheduleDayByDay(request);
                }

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
                        log.error("❌ 재시도 후에도 서사 없는 추천이 남아있어 하루씩 개별 생성으로 대체합니다.");
                        return createScheduleDayByDay(request);
                    }
                    continue;
                }

                // 파싱/서사 검증은 통과했지만, 요청한 지역과 동떨어진 좌표가 섞여 있으면 지도가 엉뚱하게 찍히므로 재시도한다
                boolean hasBadCoords = result.getDays() != null
                        && result.getDays().stream().anyMatch(day -> hasInvalidCoordinates(day.getItems(), request.getRegion()));
                if (hasBadCoords) {
                    log.warn("⚠️ 요청 지역을 벗어난 좌표가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        log.error("❌ 재시도 후에도 좌표 오류가 남아있어 하루씩 개별 생성으로 대체합니다.");
                        return createScheduleDayByDay(request);
                    }
                    continue;
                }

                // 파싱/서사/좌표 검증은 통과했지만, 같은 장소가 여러 날에 걸쳐 중복되면 날짜별로
                // 똑같은 일정이 나온 것처럼 보이므로 재시도한다
                if (hasDuplicateAcrossDays(result.getDays())) {
                    log.warn("⚠️ 날짜 간 중복된 장소가 감지되었습니다 (시도: {}). 재시도 중...", attempt + 1);
                    if (attempt == MAX_RETRIES) {
                        log.error("❌ 재시도 후에도 날짜 간 중복 장소가 남아있어 하루씩 개별 생성으로 대체합니다.");
                        return createScheduleDayByDay(request);
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
                    log.error("❌ 모든 재시도 실패. 하루씩 개별 생성으로 대체합니다.", e);
                    return createScheduleDayByDay(request);
                }
            }
        }

        return createScheduleDayByDay(request);
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

    /**
     * 응답에 좌표가 아예 없거나(null), 선택된 지역의 대략적인 범위를 벗어난 항목이 있는지 검사한다.
     * 좌표 범위는 Region enum 자체에 있으므로(latMin/latMax/lonMin/lonMax) region이 없을 때만 검증을 건너뛴다.
     */
    private boolean hasInvalidCoordinates(List<ClaudeScheduleResponse.ItemDto> items, Region region) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (region == null) {
            return false;
        }
        return items.stream().anyMatch(item -> {
            Double lat = item.getLatitude();
            Double lon = item.getLongitude();
            if (lat == null || lon == null) {
                return true;
            }
            return lat < region.getLatMin() || lat > region.getLatMax()
                    || lon < region.getLonMin() || lon > region.getLonMax();
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
     * 하루치 응답 안에서 같은 장소(placeName)가 두 번 이상 등장하는지 검사한다.
     */
    private boolean hasDuplicateItemNames(List<ClaudeScheduleResponse.ItemDto> items) {
        if (items == null || items.size() < 2) {
            return false;
        }
        Set<String> seenPlaceNames = new HashSet<>();
        for (ClaudeScheduleResponse.ItemDto item : items) {
            String placeName = item.getPlaceName();
            if (placeName == null || placeName.isBlank()) {
                continue;
            }
            if (!seenPlaceNames.add(placeName.trim().toLowerCase())) {
                return true;
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

    /**
     * 전체 일정 한 번에 생성이 실패(중복/서사부족/좌표오류/파싱실패 등)했을 때의 복구 경로.
     * 저가형 모델이 여러 날을 한 번에 요청받으면 날짜 간 중복을 잘 피하지 못하는 경향이 있어,
     * 이미 검증된 하루 재생성(callOpenRouterApiForDay) 로직을 재사용해 하루씩 개별 호출하며
     * 앞선 날짜에서 쓰인 장소는 제외 목록으로 넘긴다. 이렇게 하면 지역도 항상 요청과 일치하고,
     * 날짜별 중복도 구조적으로 발생할 수 없다. 특정 날짜의 개별 생성마저 실패하면(극히 드문 경우)
     * 그 날짜에만 안내용 자리표시자를 넣고 계속 진행한다.
     */
    private ClaudeScheduleResponse createScheduleDayByDay(AiScheduleRequest request) {
        String regionName = request.getRegion() != null ? request.getRegion().getDisplayName() : "일본 주요 도시";

        ClaudeScheduleResponse result = new ClaudeScheduleResponse();
        result.setTitle(regionName + " 추천 일정");

        int daysCount = request.getDaysCount() > 0 ? request.getDaysCount() : 2;

        // 1단계: 날짜별로 동시에 요청한다. 이 시점엔 아직 어느 날짜도 완성되지 않았으므로
        // (기존 순차 코드도) 첫 시도의 제외 목록은 항상 "None"이었다 -- 동시에 쏘아도 첫 시도
        // 자체의 동작은 달라지지 않는다. 날짜 간 중복 방지는 아래 2단계에서 사후 처리한다.
        List<CompletableFuture<ClaudeScheduleResponse.DayDto>> futures = new ArrayList<>();
        for (int i = 1; i <= daysCount; i++) {
            int dayNumber = i;
            futures.add(CompletableFuture.supplyAsync(
                    () -> generateSingleDay(request, dayNumber, "None", regionName), dayGenerationExecutor));
        }
        List<ClaudeScheduleResponse.DayDto> days = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 2단계: 병렬 생성 특성상 날짜끼리 서로의 결과를 모른 채 만들어졌으므로, 실제로 겹치는
        // 장소가 있는지 기존 검증(hasDuplicateAcrossDays)으로 확인하고, 겹친 날짜만 순서대로
        // (이전 날짜들의 장소를 제외 목록으로 넘겨) 다시 생성한다. 대부분은 안 겹쳐서 이 블록 자체가 안 돈다.
        if (hasDuplicateAcrossDays(days)) {
            Set<String> usedPlaceNames = new LinkedHashSet<>();
            for (int idx = 0; idx < days.size(); idx++) {
                ClaudeScheduleResponse.DayDto day = days.get(idx);
                List<ClaudeScheduleResponse.ItemDto> items = day.getItems();

                boolean overlapsUsedPlaces = items != null && items.stream()
                        .map(ClaudeScheduleResponse.ItemDto::getPlaceName)
                        .filter(name -> name != null && !name.isBlank())
                        .anyMatch(name -> usedPlaceNames.contains(name.trim().toLowerCase()));

                if (overlapsUsedPlaces) {
                    int dayNumber = day.getDayNumber();
                    // 원래 순차 로직처럼(MAX_RETRIES회) 겹치지 않을 때까지 재시도한다. 한 번만 시도하면
                    // 저가형 모델이 좁은 테마(예: 온천/카페 위주 요청)에서 같은 후보를 계속 반복 추천해
                    // 여전히 겹친 채로 끝나버리는 경우가 있어, 이 재시도 횟수는 줄이면 안 된다.
                    ClaudeScheduleResponse.DayDto regenerated = day;
                    for (int retryAttempt = 0; retryAttempt <= MAX_RETRIES; retryAttempt++) {
                        log.warn("⚠️ {}일차가 다른 날짜와 겹쳐 다시 생성합니다 (시도: {}).", dayNumber, retryAttempt + 1);
                        String otherDaysPlaces = usedPlaceNames.isEmpty() ? "None" : String.join(", ", usedPlaceNames);
                        regenerated = generateSingleDay(request, dayNumber, otherDaysPlaces, regionName);

                        List<ClaudeScheduleResponse.ItemDto> regeneratedItems = regenerated.getItems();
                        boolean stillOverlaps = regeneratedItems != null && regeneratedItems.stream()
                                .map(ClaudeScheduleResponse.ItemDto::getPlaceName)
                                .filter(name -> name != null && !name.isBlank())
                                .anyMatch(name -> usedPlaceNames.contains(name.trim().toLowerCase()));

                        if (!stillOverlaps) {
                            break;
                        }
                    }
                    days.set(idx, regenerated);
                    items = regenerated.getItems();
                }

                if (items != null) {
                    items.stream()
                            .map(ClaudeScheduleResponse.ItemDto::getPlaceName)
                            .filter(name -> name != null && !name.isBlank())
                            .forEach(name -> usedPlaceNames.add(name.trim().toLowerCase()));
                }
            }
        }

        result.setDays(days);
        return result;
    }

    // 하루치를 생성하고, 실패하면 자리표시자로 대체한다 (기존 로직과 동일).
    // 1단계 병렬 생성과 2단계 개별 재생성이 공유하는 로직.
    private ClaudeScheduleResponse.DayDto generateSingleDay(AiScheduleRequest request, int dayNumber,
                                                              String otherDaysPlaces, String regionName) {
        ClaudeScheduleResponse.DayDto day = new ClaudeScheduleResponse.DayDto();
        day.setDayNumber(dayNumber);
        try {
            String prompt = buildNewDayPrompt(request, dayNumber, otherDaysPlaces);
            ClaudeScheduleResponse.DayDto generated = callOpenRouterApiForDay(prompt, request.getRegion());
            day.setItems(generated.getItems());
        } catch (Exception e) {
            log.error("❌ {}일차 개별 생성도 실패하여 자리표시자로 대체합니다: {}", dayNumber, e.getMessage());
            day.setItems(createPlaceholderItems(regionName, dayNumber));
        }
        return day;
    }

    /**
     * 하루 개별 생성마저 실패했을 때 넣는 안내용 자리표시자. 실제 장소를 지어내지 않고,
     * 사용자가 '하루 재생성' 버튼으로 다시 시도하도록 안내한다.
     */
    private List<ClaudeScheduleResponse.ItemDto> createPlaceholderItems(String regionName, int dayNumber) {
        ClaudeScheduleResponse.ItemDto item = new ClaudeScheduleResponse.ItemDto();
        item.setPlaceName(dayNumber + "일차 장소를 생성하지 못했어요");
        item.setPlaceAddress(regionName);
        item.setVisitTime("10:00");
        item.setVisitOrder(1);
        item.setMemo("AI가 이 날짜의 장소를 만들지 못했어요. '하루 재생성' 버튼을 눌러 다시 시도해 주세요.");
        item.setRecommendReason("일시적인 오류로 이 날짜만 자동 생성에 실패했어요. 다른 날짜는 정상적으로 생성되었으니, 이 날짜만 재생성해 주세요.");
        item.setPlaceType("ETC");
        return new ArrayList<>(List.of(item));
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
                   if it otherwise fits the local-food theme), do not include it. If the request
                   implies a tier or vibe (e.g., "luxury", "budget", "high-end", "가성비"), that
                   tier applies to the ENTIRE itinerary, not just an anchor item like the hotel --
                   e.g., a "luxury beachfront hotel" request means every meal/activity that day
                   should also read as upscale, never a cheap fast-food chain like 김밥천국/Gimbap
                   Cheonguk sitting next to a five-star hotel.
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