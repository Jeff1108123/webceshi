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

        double handoffPulse = smoothCyclicPulse((minutes + seed) % 180, 24, 180, 9.5);
        double doorOpenPulse = smoothCyclicPulse((minutes + seed) % 95, 88, 95, 4.8);
        double routeDrift = Math.sin((minutes + seed % 720) / 360.0) * 0.65;

        double temperature = 4.6
                + Math.sin(phase * 0.54) * 1.35
                + Math.cos(phase * 0.18 + seed % 11) * 0.48
                + routeDrift
                + handoffPulse * 1.65
                + doorOpenPulse * 0.72;

        double humidity = 59
                + Math.sin(phase * 0.34 + 1.5) * 6.4
                + Math.cos(phase * 0.12 + seed % 9) * 2.1
                - handoffPulse * 1.6
                + routeDrift * 1.8;

        double lightExposure = smoothCyclicPulse((minutes + seed) % 160, 154, 160, 5.8);
        double ambientLeak = Math.max(0, Math.sin(phase * 0.72 + seed % 5)) * 1.4;
        double light = 3.2 + ambientLeak + doorOpenPulse * 3.4 + lightExposure * 7.2;

        long batteryElapsedMinutes = Math.min(Math.max(minutes, 0), 30L * 24 * 60);
        int batteryLevel = (int) Math.round(clamp(
                96 - (batteryElapsedMinutes / 60.0) * 0.08 - (seed % 7) + Math.sin(phase * 0.18) * 1.2,
                34,
                99));
        boolean signalStatus = (recordedAt.getMinute() + seed) % 41 != 0;

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
