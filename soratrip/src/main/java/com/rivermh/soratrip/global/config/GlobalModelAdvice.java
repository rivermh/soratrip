package com.rivermh.soratrip.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

	@Value("${google.maps.key}")
	private String googleMapsKey;

	@ModelAttribute("googleApiKey")
	public String addGoogleApiKey() {
		return googleMapsKey;
	}
}
