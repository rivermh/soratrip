package com.rivermh.soratrip;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class SoratripApplication {

	public static void main(String[] args) {
		// 배포 환경(Railway 등)은 컨테이너 기본 시간대가 UTC라, JVM 기본 시간대를 명시적으로
		// 고정하지 않으면 @CreatedDate 등 LocalDateTime.now() 기반 타임스탬프가 로컬(KST)과
		// 9시간 어긋난다. 한국/일본 사용자 대상 서비스라 Asia/Seoul(=JST와 동일 오프셋)로 고정한다.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(SoratripApplication.class, args);
	}

}
