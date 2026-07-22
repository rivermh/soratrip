package com.rivermh.soratrip.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("message", "SoraTirp에 오신것을 환영합니다");
		return "index";
	}
}
