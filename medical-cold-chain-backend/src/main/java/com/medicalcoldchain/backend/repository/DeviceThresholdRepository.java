package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.DeviceThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceThresholdRepository extends JpaRepository<DeviceThreshold, Long> {

    Optional<DeviceThreshold> findByUserIdAndDeviceId(Long userId, Long deviceId);

    List<DeviceThreshold> findByUserIdAndDeviceIdIn(Long userId, Collection<Long> deviceIds);
}
