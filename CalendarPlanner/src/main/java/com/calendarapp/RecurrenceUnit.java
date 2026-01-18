package com.calendarapp;

public enum RecurrenceUnit {
    DAY("d"),
    WEEK("w"),
    MONTH("m");

    private final String code;

    RecurrenceUnit(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static RecurrenceUnit fromCode(String code) {
        for (RecurrenceUnit unit : values()) {
            if (unit.code.equalsIgnoreCase(code)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown recurrence unit: " + code);
    }
}