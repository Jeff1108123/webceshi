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
        double phase = (minutes + seed % 360) / 60.0;

        double temperature = 4.8
                + Math.sin(phase * 0.85) * 2.1
                + Math.cos(phase * 0.42 + seed % 7) * 0.7
                + smoothCyclicPulse((recordedAt.getHour() + seed) % 12, 0, 12, 1.8) * 2.4;

        double humidity = 58
                + Math.sin(phase * 0.55 + 1.5) * 9.2
                + Math.cos(phase * 0.21 + seed % 9) * 2.3;

        double light = 5.5
                + Math.max(0, Math.sin(phase * 1.9 + seed % 5)) * 4.8
                + smoothCyclicPulse((recordedAt.getMinute() + seed) % 50, 45, 50, 7.0) * 3.4;

        int batteryLevel = (int) Math.round(clamp(
                96 - ((minutes % (48 * 60)) / 60.0) * 0.8 - (seed % 8) + Math.sin(phase) * 1.5,
                32,
                99));
        boolean signalStatus = (recordedAt.getMinute() + seed) % 23 != 0;

        return new SimulatedTelemetry(
                round(temperature),
                round(humidity),
                round(light),
                batteryLevel,
                signalStatus
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

        double longitude = round(from.longitude + (to.longitude - from.longitude) * ratio);
        double latitude = round(from.latitude + (to.latitude - from.latitude) * ratio);
        String address = routeName + " - " + (ratio < 0.5 ? from.address : to.address);

        return new SimulatedLocation(longitude, latitude, from.city, address);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double smoothCyclicPulse(double value, double center, double period, double width) {
        double directDistance = Math.abs(value - center);
        double distance = Math.min(directDistance, period - directDistance);
        return Math.exp(-(distance * distance) / (2 * width * width));
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
