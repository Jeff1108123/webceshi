package com.medicalcoldchain.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceSimulationService {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final List<RoutePoint> ROUTE_POINTS = List.of(
            new RoutePoint(121.4666, 31.2284, "上海", "上海市静安区医药冷链中心"),
            new RoutePoint(121.4728, 31.2085, "上海", "上海交通大学医学院附属瑞金医院"),
            new RoutePoint(121.4382, 31.1724, "上海", "上海市第六人民医院冷链交接点"),
            new RoutePoint(121.4103, 31.1968, "上海", "徐汇区疫苗转运中继站"),
            new RoutePoint(121.4428, 31.2217, "上海", "黄浦区生物样本配送枢纽")
    );

    public SimulatedTelemetry simulateTelemetry(String deviceCode, LocalDateTime recordedAt) {
        long minutes = Duration.between(BASE_TIME, recordedAt).toMinutes();
        int seed = Math.abs(deviceCode.hashCode());
        double phase = (minutes + seed % 1440) / 1440.0;
        double slowPhase = (minutes + seed % 10080) / 10080.0;

        double temperature = clamp(
                25
                        + Math.sin(phase * Math.PI * 2) * 3.1
                        + Math.cos(slowPhase * Math.PI * 2 + seed % 13) * 1.2
                        + Math.sin(phase * Math.PI * 4 + seed % 7) * 0.55,
                20,
                30);

        double humidity = clamp(
                55
                        + Math.sin(phase * Math.PI * 2 + 1.2 + seed % 5) * 8.5
                        + Math.cos(slowPhase * Math.PI * 2 + seed % 11) * 4.2
                        - (temperature - 25) * 0.6,
                40,
                70);

        double light = clamp(
                10
                        + Math.sin(phase * Math.PI * 2 + 0.7 + seed % 9) * 1.7
                        + Math.cos(slowPhase * Math.PI * 4 + seed % 3) * 0.8,
                7,
                13);

        int batteryLevel = (int) Math.round(clamp(
                84
                        + Math.sin(slowPhase * Math.PI * 2 + seed % 17) * 8
                        + Math.cos(phase * Math.PI * 2) * 2,
                65,
                98));

        return new SimulatedTelemetry(
                round(temperature),
                round(humidity),
                round(light),
                batteryLevel,
                true
        );
    }

    public SimulatedLocation simulateLocation(String deviceCode, String routeName, LocalDateTime recordedAt) {
        long minutes = Duration.between(BASE_TIME, recordedAt).toMinutes();
        int seed = Math.abs(deviceCode.hashCode());
        double progress = ((minutes / 18.0) + seed % 120) / 10.0;

        int currentIndex = ((int) Math.floor(progress)) % ROUTE_POINTS.size();
        double ratio = progress - Math.floor(progress);

        RoutePoint from = ROUTE_POINTS.get(currentIndex);
        RoutePoint to = ROUTE_POINTS.get((currentIndex + 1) % ROUTE_POINTS.size());

        double longitude = roundCoordinate(from.longitude + (to.longitude - from.longitude) * ratio);
        double latitude = roundCoordinate(from.latitude + (to.latitude - from.latitude) * ratio);
        String address = routeName + " - " + (ratio < 0.5 ? from.address : to.address);

        return new SimulatedLocation(longitude, latitude, from.city, address);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundCoordinate(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RoutePoint(double longitude, double latitude, String city, String address) {
    }

    public record SimulatedTelemetry(
            double temperature,
            double humidity,
            double light,
            int batteryLevel,
            boolean signalStatus
    ) {
    }

    public record SimulatedLocation(
            double longitude,
            double latitude,
            String city,
            String address
    ) {
    }
}
