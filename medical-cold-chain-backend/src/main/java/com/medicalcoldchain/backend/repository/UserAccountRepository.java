package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByPhone(String phone);
    boolean existsByPhone(String phone);
    java.util.List<UserAccount> findAllByOrderByCreatedAtAsc();
}
