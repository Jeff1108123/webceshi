package com.medicalcoldchain.backend.entity;

import com.medicalcoldchain.backend.enums.DeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transport_device")
public class TransportDevice extends AbstractAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String deviceCode;

    @Column(nullable = false, length = 100)
    private String deviceName;

    @Column(nullable = false, length = 100)
    private String medicineName;

    @Column(nullable = false, length = 100)
    private String routeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_user_id")
    private UserAccount currentUser;

    @Column
    private LocalDateTime borrowedAt;

    @Column(nullable = false)
    private Integer batteryLevel;

    @Column(nullable = false)
    private Boolean signalStatus;
}
