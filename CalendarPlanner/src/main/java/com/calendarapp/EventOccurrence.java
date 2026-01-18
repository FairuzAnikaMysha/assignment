package com.calendarapp;

import java.time.LocalDateTime;

public class EventOccurrence {
    private final int eventId;
    private final String title;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public EventOccurrence(int eventId, String title, LocalDateTime start, LocalDateTime end) {
        this.eventId = eventId;
        this.title = title;
        this.start = start;
        this.end = end;
    }

    public int getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public String gettitle() {
        return title;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getstart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }
}