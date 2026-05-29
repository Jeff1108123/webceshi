package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.DeviceLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DeviceLocationRepository extends JpaRepository<DeviceLocation, Long> {

    Optional<DeviceLocation> findTopByDeviceIdOrderByRecordedAtDesc(Long deviceId);

    void deleteByDeviceIdAndRecordedAtGreaterThanEqual(Long deviceId, LocalDateTime recordedAt);

    void deleteByDeviceId(Long deviceId);

    void deleteAllBy();
}
