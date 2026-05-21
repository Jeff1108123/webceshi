package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.TelemetryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryRecordRepository extends JpaRepository<TelemetryRecord, Long> {

    Optional<TelemetryRecord> findTopByDeviceIdOrderByRecordedAtDesc(Long deviceId);

    List<TelemetryRecord> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long deviceId, LocalDateTime start, LocalDateTime end);

    void deleteByDeviceIdAndRecordedAtGreaterThanEqual(Long deviceId, LocalDateTime recordedAt);

    void deleteAllBy();
}
