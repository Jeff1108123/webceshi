package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.LoginCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {

    Optional<LoginCode> findTopByPhoneAndCodeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String phone, String code, LocalDateTime now);
}
