package com.rivermh.soratrip.domain.schedule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.GeminiScheduleResponse;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
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
public class GeminiScheduleService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    

    public Long createScheduleWithAi(AiScheduleRequest request, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 1. Gemini API 프롬프트 구성
        String prompt = buildPrompt(request);

        // 2. Gemini API 호출 (실패 시 Fallback 더미 데이터 반환)
        GeminiScheduleResponse aiResponse = callGeminiApi(prompt, request);

        // 3. 응답받은 데이터를 DB 엔티티로 변환 및 저장
        LocalDate startDate = LocalDate.now().plusDays(1);
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
            for (GeminiScheduleResponse.DayDto dayDto : aiResponse.getDays()) {
                ScheduleDay day = ScheduleDay.builder()
                        .travelSchedule(schedule)
                        .dayNumber(dayDto.getDayNumber())
                        .visitDate(startDate.plusDays(dayDto.getDayNumber() - 1))
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

    private GeminiScheduleResponse callGeminiApi(String promptText, AiScheduleRequest request) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(Map.of("text", promptText)));
        requestBody.put("contents", List.of(content));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("response_mime_type", "application/json");
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String jsonText = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            log.info("Gemini API 호출 성공!");
            return objectMapper.readValue(jsonText, GeminiScheduleResponse.class);
        } catch (Exception e) {
            log.warn("Gemini API 호출 실패 (429/네트워크 오류 발생). Fallback 일정 데이터를 생성합니다. 원인: {}", e.getMessage());
            return createFallbackSchedule(request);
        }
    }

    // API 장애 또는 429 Rate Limit 발생 시 사용할 백업(Fallback) 더미 일정 생성
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

            // Day 1 샘플 장소 (시부야 & 도쿄타워)
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
                // Day 2 이상 샘플 장소 (아사쿠사 & 우에노)
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
        return String.format("""
                You are an expert Japan travel planner focusing on accessibility and luggage-friendly routing.
                Create a detailed %d-day itinerary for %s, Japan.
                
                Constraints & Context:
                - Companion Type: %s
                - Travel Tags: %s
                - Additional Instructions: %s
                
                JSON Format Requirement:
                Return ONLY a JSON object with this exact structure (no markdown, no extra text):
                {
                  "title": "Concise itinerary title in Korean",
                  "days": [
                    {
                      "dayNumber": 1,
                      "items": [
                        {
                          "placeName": "Real Japanese place name in Korean",
                          "placeAddress": "Full Japanese address",
                          "latitude": 35.681236,
                          "longitude": 139.767125,
                          "visitTime": "10:00",
                          "visitOrder": 1,
                          "memo": "Practical travel tip in Korean focusing on luggage/stairs/elevators"
                        }
                      ]
                    }
                  ]
                }
                Make sure latitude and longitude coordinates are accurate real-world values for Tokyo/Japan location.
                """,
                req.getDaysCount(),
                req.getRegion().name(),
                req.getCompanionType() != null ? req.getCompanionType() : "General",
                req.getTags() != null ? req.getTags().toString() : "None",
                req.getExtraPrompt() != null ? req.getExtraPrompt() : "None"
        );
    }
}