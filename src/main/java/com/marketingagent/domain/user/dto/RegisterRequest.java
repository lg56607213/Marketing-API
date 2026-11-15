package com.marketingagent.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param companyName 회원가입 시 새로 만들 업체 이름. 비우면 이메일로 대신한다.
 *                    관리자가 자기 업체에 사용자를 추가할 때는 쓰지 않는다.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @Size(max = 100) String companyName
) {}
