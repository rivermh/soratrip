package com.rivermh.soratrip.domain.schedule.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleExportService {

    private final SpringTemplateEngine templateEngine;

    private static final DateTimeFormatter ICS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter ICS_LOCAL_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    // 일정 방문 시간이 비어있을 때 기본으로 사용할 시각 (오전 10시)
    private static final LocalTime DEFAULT_VISIT_TIME = LocalTime.of(10, 0);

    /**
     * 일정을 RFC5545(iCalendar) 형식 문자열로 변환한다.
     * 각 장소(ScheduleItem)를 1시간짜리 이벤트로 매핑하며, 방문 시간이 없으면 기본값(오전 10시)을 사용한다.
     */
    public String generateIcs(TravelSchedule schedule) {
        String tzId = schedule.getRegion() != null ? schedule.getRegion().getTimezoneId() : "Asia/Tokyo";
        String dtStamp = LocalDateTime.now(ZoneOffset.UTC).format(ICS_TIMESTAMP);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//SoraTrip//Schedule Export//KO\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");

        for (ScheduleDay day : schedule.getDays()) {
            for (ScheduleItem item : day.getItems()) {
                LocalTime visitTime = item.getVisitTime() != null ? item.getVisitTime() : DEFAULT_VISIT_TIME;
                LocalDateTime start = LocalDateTime.of(day.getVisitDate(), visitTime);
                LocalDateTime end = start.plusHours(1);

                sb.append("BEGIN:VEVENT\r\n");
                sb.append("UID:soratrip-").append(schedule.getId()).append('-').append(item.getId()).append("@soratrip\r\n");
                sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
                sb.append("DTSTART;TZID=").append(tzId).append(':').append(start.format(ICS_LOCAL_TIME)).append("\r\n");
                sb.append("DTEND;TZID=").append(tzId).append(':').append(end.format(ICS_LOCAL_TIME)).append("\r\n");
                sb.append("SUMMARY:").append(escapeIcsText(item.getPlaceName())).append("\r\n");
                if (item.getPlaceAddress() != null && !item.getPlaceAddress().isBlank()) {
                    sb.append("LOCATION:").append(escapeIcsText(item.getPlaceAddress())).append("\r\n");
                }
                String description = item.getMemo();
                if (description != null && !description.isBlank()) {
                    sb.append("DESCRIPTION:").append(escapeIcsText(description)).append("\r\n");
                }
                sb.append("END:VEVENT\r\n");
            }
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String escapeIcsText(String text) {
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /**
     * 일정을 PDF 바이트 배열로 변환한다.
     * Thymeleaf로 렌더링한 HTML을 jsoup으로 정리해(XHTML 문법 보장) openhtmltopdf에 전달한다.
     * 한글/일본어 렌더링을 위해 나눔고딕 폰트를 직접 임베드한다.
     */
    public byte[] generatePdf(TravelSchedule schedule, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("schedule", schedule);

        String rawHtml = templateEngine.process("schedule/pdf", context);

        Document jsoupDoc = org.jsoup.Jsoup.parse(rawHtml);
        jsoupDoc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8);
        String xhtml = jsoupDoc.html();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // fat jar 패키징 환경에서도 안전하게 읽히도록 File이 아닌 InputStream 공급자로 폰트 등록
            builder.useFont(this::loadFontStream, "NanumGothic");
            builder.withHtmlContent(xhtml, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", e);
        }
    }

    private InputStream loadFontStream() {
        try {
            return new ClassPathResource("fonts/NanumGothic-Regular.ttf").getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 폰트 파일을 읽을 수 없습니다.", e);
        }
    }
}
