package com.medicalcoldchain.backend.util;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }
}
