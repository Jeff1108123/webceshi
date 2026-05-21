package com.medicalcoldchain.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "device_threshold", uniqueConstraints = {
        @UniqueConstraint(name = "uk_threshold_user_device", columnNames = {"user_id", "device_id"})
})
public class DeviceThreshold extends AbstractAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private TransportDevice device;

    @Column(nullable = false)
    private Double tempMin;

    @Column(nullable = false)
    private Double tempMax;

    @Column(nullable = false)
    private Double humidityMin;

    @Column(nullable = false)
    private Double humidityMax;

    @Column(nullable = false)
    private Double lightMax;

    @Column(nullable = false)
    private Integer durationLimitHours;
}
