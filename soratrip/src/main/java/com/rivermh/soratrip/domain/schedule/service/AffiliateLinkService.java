package com.rivermh.soratrip.domain.schedule.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rivermh.soratrip.domain.post.entity.Region;

// 아고다/부킹닷컴 제휴(딥링크) URL 생성. cid/aid는 제휴 승인 후 application-secret.yml에 설정.
@Service
public class AffiliateLinkService {

    @Value("${affiliate.agoda.cid:}")
    private String agodaCid;

    @Value("${affiliate.booking.aid:}")
    private String bookingAid;

    public String buildAgodaLink(Region region, LocalDate startDate, LocalDate endDate) {
        StringBuilder url = new StringBuilder("https://www.agoda.com/ko-kr/search?textToSearch=")
                .append(encode(region.getKoName()));

        if (startDate != null) {
            long los = (endDate != null && endDate.isAfter(startDate))
                    ? ChronoUnit.DAYS.between(startDate, endDate)
                    : 1;
            url.append("&checkIn=").append(startDate).append("&los=").append(los);
        }
        if (!agodaCid.isBlank()) {
            url.append("&cid=").append(encode(agodaCid));
        }
        return url.toString();
    }

    public String buildBookingLink(Region region, LocalDate startDate, LocalDate endDate) {
        StringBuilder url = new StringBuilder("https://www.booking.com/searchresults.ko.html?ss=")
                .append(encode(region.getKoName()));

        if (startDate != null) {
            LocalDate checkOut = (endDate != null && endDate.isAfter(startDate)) ? endDate : startDate.plusDays(1);
            url.append("&checkin=").append(startDate).append("&checkout=").append(checkOut);
        }
        if (!bookingAid.isBlank()) {
            url.append("&aid=").append(encode(bookingAid));
        }
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
