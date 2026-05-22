package com.medicalcoldchain.backend.repository;

import com.medicalcoldchain.backend.entity.DeviceBorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceBorrowRecordRepository extends JpaRepository<DeviceBorrowRecord, Long> {

    List<DeviceBorrowRecord> findAllByOrderByBorrowTimeDesc();

    Optional<DeviceBorrowRecord> findTopByDeviceIdAndReturnTimeIsNullOrderByBorrowTimeDesc(Long deviceId);

    Optional<DeviceBorrowRecord> findTopByDeviceIdAndBorrowerIdAndReturnTimeIsNullOrderByBorrowTimeDesc(
            Long deviceId,
            Long borrowerId
    );

    long countByBorrowerId(Long borrowerId);

    void deleteByBorrowerId(Long borrowerId);

    void deleteAllBy();
}
