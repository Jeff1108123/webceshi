package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.TransportDevice;
import com.medicalcoldchain.backend.enums.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransportDeviceRepository extends JpaRepository<TransportDevice, Long> {

    Optional<TransportDevice> findByDeviceCode(String deviceCode);

    List<TransportDevice> findAllByOrderByDeviceCodeAsc();

    List<TransportDevice> findByCurrentUserIdOrderByDeviceCodeAsc(Long userId);

    long countByStatus(DeviceStatus status);

    List<TransportDevice> findByStatusOrderByDeviceCodeAsc(DeviceStatus status);

    Optional<TransportDevice> findByIdAndCurrentUserId(Long id, Long userId);
}
