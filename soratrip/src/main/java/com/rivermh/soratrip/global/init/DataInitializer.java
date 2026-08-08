package com.rivermh.soratrip.global.init;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final MemberRepository memberRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (memberRepository.count() > 10) {
                log.info("이미 테스트 데이터가 존재하여 생략합니다.");
                return;
            }

            log.info("대량 테스트 데이터(200명) 생성 시작...");

            List<ScheduleTag> allTags = List.of(ScheduleTag.values());
            List<Member> dummyMembers = new ArrayList<>();

            for (int i = 1; i <= 200; i++) {
                Set<ScheduleTag> randomTags = new HashSet<>();
                if (!allTags.isEmpty()) {
                    randomTags.add(allTags.get(i % allTags.size()));
                    if (allTags.size() > 1) {
                        randomTags.add(allTags.get((i * 3) % allTags.size()));
                    }
                }

                // 1. 빌더로는 기본 정보만 생성
                Member member = Member.builder()
                        .email("user" + i + "@test.com")
                        .password("{noop}1234")
                        .nickname("여행자_" + i)
                        .role(Role.USER)
                        .build();

                // 2. 이미 구현되어 있는 updatePreferredTags 메서드로 태그 주입
                member.updatePreferredTags(randomTags);

                dummyMembers.add(member);
            }

            memberRepository.saveAll(dummyMembers);
            log.info("대량 테스트 회원 200명 생성 완료!");
        };
    }
}