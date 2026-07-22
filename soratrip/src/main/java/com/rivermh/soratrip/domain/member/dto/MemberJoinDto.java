package com.rivermh.soratrip.domain.member.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberJoinDto {

	private String email;
	private String password;
	private String nickname;
}
