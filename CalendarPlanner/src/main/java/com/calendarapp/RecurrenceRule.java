package com.calendarapp;

import java.time.LocalDate;

public class RecurrenceRule {
    private final int eventId;
    private final int intervalCount;
    private final RecurrenceUnit unit;
    private final int times;
    private final LocalDate endDate;

    public RecurrenceRule(int eventId, int intervalCount, RecurrenceUnit unit, int times, LocalDate endDate) {
        this.eventId = eventId;
        this.intervalCount = intervalCount;
        this.unit = unit;
        this.times = times;
        this.endDate = endDate;
    }

    public int getEventId() {
        return eventId;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public RecurrenceUnit getUnit() {
        return unit;
    }

    public int getTimes() {
        return times;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String toIntervalString() {
        return intervalCount + unit.getCode();
    }

    public static RecurrenceRule parse(int eventId, String interval, int times, String endDateRaw) {
        String trimmed = interval.trim().toLowerCase();
        int count = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
        RecurrenceUnit unit = RecurrenceUnit.fromCode(trimmed.substring(trimmed.length() - 1));
        LocalDate endDate = endDateRaw == null || endDateRaw.equals("0") || endDateRaw.trim().isEmpty()
                ? null
                : LocalDate.parse(endDateRaw);
        return new RecurrenceRule(eventId, count, unit, times, endDate);
    }
}