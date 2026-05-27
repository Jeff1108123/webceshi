package com.medicalcoldchain.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "device_borrow_record")
public class DeviceBorrowRecord extends AbstractAuditableEntity {

    public static final double DEFAULT_TEMP_MIN = 20D;
    public static final double DEFAULT_TEMP_MAX = 30D;
    public static final double DEFAULT_HUMIDITY_MIN = 40D;
    public static final double DEFAULT_HUMIDITY_MAX = 70D;
    public static final double DEFAULT_LIGHT_MAX = 13D;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private TransportDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private UserAccount borrower;

    @Column(nullable = false)
    private LocalDateTime borrowTime;

    @Column(nullable = false, columnDefinition = "double default 20")
    @Builder.Default
    private Double tempMin = DEFAULT_TEMP_MIN;

    @Column(nullable = false, columnDefinition = "double default 30")
    @Builder.Default
    private Double tempMax = DEFAULT_TEMP_MAX;

    @Column(nullable = false, columnDefinition = "double default 40")
    @Builder.Default
    private Double humidityMin = DEFAULT_HUMIDITY_MIN;

    @Column(nullable = false, columnDefinition = "double default 70")
    @Builder.Default
    private Double humidityMax = DEFAULT_HUMIDITY_MAX;

    @Column(nullable = false, columnDefinition = "double default 13")
    @Builder.Default
    private Double lightMax = DEFAULT_LIGHT_MAX;

    @Column
    private LocalDateTime returnTime;

    @PrePersist
    @PreUpdate
    private void applyDefaultThresholds() {
        if (tempMin == null) {
            tempMin = DEFAULT_TEMP_MIN;
        }
        if (tempMax == null) {
            tempMax = DEFAULT_TEMP_MAX;
        }
        if (humidityMin == null) {
            humidityMin = DEFAULT_HUMIDITY_MIN;
        }
        if (humidityMax == null) {
            humidityMax = DEFAULT_HUMIDITY_MAX;
        }
        if (lightMax == null) {
            lightMax = DEFAULT_LIGHT_MAX;
        }
    }
}
