package com.winthier.claims.util;

import com.winthier.claims.Claims;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class Strings {
    private Strings() { }

    public static String fold(List<String> items, String token) {
        if (items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(items.get(0));
        for (int i = 1; i < items.size(); ++i) {
            sb.append(token).append(items.get(i));
        }
        return sb.toString();
    }

    public static String genitive(String name) {
        if (name == null) return "someone's";
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("z")) return name + "'";
        return name + "'s";
    }

    public static String formatDate(Date time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        DateFormatSymbols symbols = DateFormatSymbols.getInstance();
        return String.format("%s %02d %d, %02d:%02d",
                             symbols.getShortMonths()[cal.get(Calendar.MONTH)],
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE));
    }

    public static String formatDateNow() {
        return formatDate(new Date());
    }

    public static String formatSeconds(long seconds) {
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        return String.format("%02d:%02d:%02d", hours, minutes % 60L, seconds % 60L);
    }

    public static String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int days = hours / 24;
        return String.format("%dd %02d:%02d", days, hours % 24, minutes % 60);
    }

    public static List<String> serializeUuids(List<UUID> uuids) {
        List<String> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) result.add(uuid.toString());
        return result;
    }

    public static List<UUID> deserializeUuids(List<String> strings) {
        List<UUID> result = new ArrayList<>(strings.size());
        for (String string : strings) result.add(UUID.fromString(string));
        return result;
    }

    public static String upperCaseWord(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

    public static String format(String message, Object... args) {
        return Claims.getInstance().getDelegate().format(message, args);
    }
}
