package com.rivermh.soratrip.domain.schedule.service;

import com.rivermh.soratrip.domain.schedule.dto.DayWeatherSummaryDto;
import com.rivermh.soratrip.domain.schedule.dto.OpenWeatherResponseDto;
import com.rivermh.soratrip.domain.schedule.dto.WeatherInfoDto; // 👈 추가된 DTO 임포트
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherService {

    // OpenWeatherMap 무료 플랜(5 Day / 3 Hour Forecast)이 실제로 커버하는 날짜 범위
    private static final int FORECAST_MAX_DAYS_AHEAD = 5;
    private static final DateTimeFormatter FORECAST_DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.url}")
    private String apiUrl;

    private final TravelScheduleRepository travelScheduleRepository;

    // 커넥션/응답 타임아웃을 명시하지 않으면 외부 API가 응답하지 않을 때 요청 스레드가 무한정 대기할 수 있어 타임아웃을 지정
    private final RestClient restClient = buildRestClient();

    private static RestClient buildRestClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 일정 전체의 일자별 날씨 요약 정보를 가져옵니다. (weather.html 전용)
     * days/items를 미리 Fetch Join으로 조회한 뒤, 외부 API 호출(최대 N번의 블로킹 HTTP 요청)은
     * DB 트랜잭션 밖에서 수행해 그 시간만큼 커넥션 풀을 점유하지 않도록 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<DayWeatherSummaryDto> getScheduleWeatherSummary(Long scheduleId) {
        TravelSchedule schedule = travelScheduleRepository.findByIdWithDaysAndItems(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        List<DayWeatherSummaryDto> summaryList = new ArrayList<>();

        for (ScheduleDay day : schedule.getDays()) {
            // 좌표가 등록된 첫 번째 장소를 찾음
            Optional<ScheduleItem> targetItem = day.getItems().stream()
                    .filter(item -> item.getLatitude() != null && item.getLongitude() != null)
                    .findFirst();

            if (targetItem.isEmpty()) {
                summaryList.add(DayWeatherSummaryDto.builder()
                        .dayNumber(day.getDayNumber())
                        .visitDate(day.getVisitDate())
                        .representativePlace("등록된 장소 없음")
                        .hasCoordinates(false)
                        .outfitTip("장소 좌표 정보가 없어 날씨를 불러올 수 없습니다.")
                        .build());
                continue;
            }

            ScheduleItem item = targetItem.get();
            DayWeatherSummaryDto weatherDto = fetchWeatherForDay(day, item);
            summaryList.add(weatherDto);
        }

        return summaryList;
    }

    /**
     * 특정 좌표(위도, 경도)와 방문 날짜 기준 단건 날씨 정보 조회 (ScheduleApiController 연동용)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WeatherInfoDto getWeatherForecast(Double lat, Double lon, LocalDate visitDate) {
        if (lat == null || lon == null || visitDate == null || !isWithinForecastWindow(visitDate)) {
            return null;
        }

        try {
            OpenWeatherResponseDto response = restClient.get()
                    .uri(apiUrl + "/forecast?lat={lat}&lon={lon}&appid={key}&units=metric&lang=kr",
                            lat, lon, apiKey)
                    .retrieve()
                    .body(OpenWeatherResponseDto.class);

            if (response == null || response.getList() == null || response.getList().isEmpty()) {
                return null;
            }

            Optional<OpenWeatherResponseDto.ForecastItem> matched = findMatchingForecast(response, visitDate);
            if (matched.isEmpty()) {
                // 무료 플랜의 5일 예보 범위 안에 있는 날짜인데도 매칭되는 슬롯이 없는 예외 케이스
                // (임의로 다른 날짜의 예보를 보여주면 안 되므로 조회 실패로 처리)
                return null;
            }

            return toWeatherInfoDto(matched.get());

        } catch (Exception e) {
            log.error("단건 날씨 정보 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private DayWeatherSummaryDto fetchWeatherForDay(ScheduleDay day, ScheduleItem item) {
        if (!isWithinForecastWindow(day.getVisitDate())) {
            return createFallbackDto(day, item.getPlaceName(),
                    "OpenWeatherMap 무료 플랜은 오늘부터 최대 " + FORECAST_MAX_DAYS_AHEAD + "일 이내의 예보만 제공하여, " +
                    "이 날짜의 날씨는 아직 확인할 수 없습니다.");
        }

        try {
            OpenWeatherResponseDto response = restClient.get()
                    .uri(apiUrl + "/forecast?lat={lat}&lon={lon}&appid={key}&units=metric&lang=kr",
                            item.getLatitude(), item.getLongitude(), apiKey)
                    .retrieve()
                    .body(OpenWeatherResponseDto.class);

            if (response == null || response.getList() == null || response.getList().isEmpty()) {
                return createFallbackDto(day, item.getPlaceName(), "날씨 정보를 불러오는 중 오류가 발생했습니다.");
            }

            Optional<OpenWeatherResponseDto.ForecastItem> matched = findMatchingForecast(response, day.getVisitDate());
            if (matched.isEmpty()) {
                // 예보 범위 안 날짜인데도 정확히 일치하는 슬롯이 없는 경우, 다른 날짜의 예보를 잘못 표시하지 않고
                // 조회 실패로 명확히 안내한다 (과거에는 list.get(0)으로 임의 대체하여 엉뚱한 날짜 예보가 노출되던 버그)
                return createFallbackDto(day, item.getPlaceName(),
                        "해당 날짜의 예보 데이터를 찾을 수 없습니다. 잠시 후 다시 확인해 주세요.");
            }

            return toDayWeatherSummaryDto(day, item.getPlaceName(), matched.get());

        } catch (Exception e) {
            log.error("날씨 정보 조회 실패: {}", e.getMessage());
            return createFallbackDto(day, item.getPlaceName(), "날씨 정보를 불러오는 중 오류가 발생했습니다.");
        }
    }

    /** 오늘부터 OpenWeatherMap 무료 플랜(5일 예보)이 커버하는 범위 안의 날짜인지 확인 */
    private boolean isWithinForecastWindow(LocalDate visitDate) {
        LocalDate today = LocalDate.now();
        return !visitDate.isBefore(today) && !visitDate.isAfter(today.plusDays(FORECAST_MAX_DAYS_AHEAD));
    }

    /** 방문 날짜와 정확히 일치하는(가장 이른 시간대) 예보 슬롯만 찾는다 — 없으면 다른 날짜로 대체하지 않고 empty 반환 */
    private Optional<OpenWeatherResponseDto.ForecastItem> findMatchingForecast(OpenWeatherResponseDto response, LocalDate visitDate) {
        return response.getList().stream()
                .filter(f -> LocalDateTime.parse(f.getDtTxt(), FORECAST_DT_FORMATTER).toLocalDate().equals(visitDate))
                .min(Comparator.comparing(f -> LocalDateTime.parse(f.getDtTxt(), FORECAST_DT_FORMATTER)));
    }

    private WeatherInfoDto toWeatherInfoDto(OpenWeatherResponseDto.ForecastItem matched) {
        double temp = Math.round(matched.getMain().getTemp() * 10) / 10.0;
        int pop = (int) Math.round((matched.getPop() != null ? matched.getPop() : 0.0) * 100);
        String mainCondition = matched.getWeather().get(0).getMain();
        String description = matched.getWeather().get(0).getDescription();
        String icon = matched.getWeather().get(0).getIcon();

        return WeatherInfoDto.builder()
                .mainCondition(mainCondition)
                .description(description)
                .iconUrl("https://openweathermap.org/img/wn/" + icon + "@2x.png")
                .temp(temp)
                .pop(pop)
                .outfitTip(generateOutfitTip(temp, pop, mainCondition))
                .build();
    }

    private DayWeatherSummaryDto toDayWeatherSummaryDto(ScheduleDay day, String placeName, OpenWeatherResponseDto.ForecastItem matched) {
        double temp = Math.round(matched.getMain().getTemp() * 10) / 10.0;
        int pop = (int) Math.round((matched.getPop() != null ? matched.getPop() : 0.0) * 100);
        String mainCondition = matched.getWeather().get(0).getMain();
        String description = matched.getWeather().get(0).getDescription();
        String icon = matched.getWeather().get(0).getIcon();

        return DayWeatherSummaryDto.builder()
                .dayNumber(day.getDayNumber())
                .visitDate(day.getVisitDate())
                .representativePlace(placeName)
                .temp(temp)
                .pop(pop)
                .weatherCondition(description)
                .iconUrl("https://openweathermap.org/img/wn/" + icon + "@2x.png")
                .outfitTip(generateOutfitTip(temp, pop, mainCondition))
                .hasCoordinates(true)
                .build();
    }

    private DayWeatherSummaryDto createFallbackDto(ScheduleDay day, String placeName, String message) {
        return DayWeatherSummaryDto.builder()
                .dayNumber(day.getDayNumber())
                .visitDate(day.getVisitDate())
                .representativePlace(placeName)
                .hasCoordinates(false)
                .outfitTip(message)
                .build();
    }

    private String generateOutfitTip(double temp, int pop, String condition) {
        StringBuilder tip = new StringBuilder();
        if (pop >= 50 || "Rain".equalsIgnoreCase(condition)) {
            tip.append("☔ 비 예보가 있습니다. 접이식 우산을 꼭 챙기세요! ");
        }
        if (temp >= 28) {
            tip.append("☀️ 덥고 습한 날씨입니다. 통풍이 잘 되는 반팔과 선글라스를 추천합니다.");
        } else if (temp >= 20) {
            tip.append("🌿 야외 활동하기 좋은 날씨입니다. 얇은 가디건이나 셔츠를 걸치면 좋습니다.");
        } else if (temp >= 10) {
            tip.append("🍂 쌀쌀한 날씨입니다. 자켓이나 트렌치코트를 준비해 주세요.");
        } else {
            tip.append("❄️ 추운 날씨입니다. 두꺼운 코트나 패딩, 핫팩을 챙기세요.");
        }
        return tip.toString();
    }
}