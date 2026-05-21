package com.medicalcoldchain.backend.controller;

import com.medicalcoldchain.backend.common.ApiResponse;
import com.medicalcoldchain.backend.dto.auth.LoginRequest;
import com.medicalcoldchain.backend.dto.auth.LoginResponse;
import com.medicalcoldchain.backend.dto.auth.SendCodeRequest;
import com.medicalcoldchain.backend.dto.auth.SendCodeResponse;
import com.medicalcoldchain.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-code")
    public ApiResponse<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ApiResponse.ok("验证码发送成功", authService.sendCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

}
