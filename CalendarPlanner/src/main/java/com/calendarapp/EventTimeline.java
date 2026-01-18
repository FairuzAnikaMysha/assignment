package com.calendarapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EventTimeline {
    private EventTimeline() {
    }

    public static List<EventOccurrence> expandOccurrences(
            Event event,
            RecurrenceRule rule,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<EventOccurrence> occurrences = new ArrayList<>();
        if (event == null) {
            return occurrences;
        }
        LocalDateTime occurrenceStart = event.getStart();
        LocalDateTime occurrenceEnd = event.getEnd();
        int produced = 0;
        int target = rule == null ? 1 : Math.max(1, rule.getTimes());
        LocalDate limitDate = rule == null ? null : rule.getEndDate();

        while (true) {
            LocalDate occurrenceDate = occurrenceStart.toLocalDate();
            boolean inRange = !occurrenceDate.isBefore(startDate) && !occurrenceDate.isAfter(endDate);
            if (inRange) {
                occurrences.add(new EventOccurrence(event.getId(), event.getTitle(), occurrenceStart, occurrenceEnd));
            }

            produced++;
            if (rule == null) {
                break;
            }
            if (rule.getTimes() > 0 && produced >= target) {
                break;
            }
            occurrenceStart = advance(occurrenceStart, rule);
            occurrenceEnd = advance(occurrenceEnd, rule);
            if (limitDate != null && occurrenceStart.toLocalDate().isAfter(limitDate)) {
                break;
            }
        }

        return occurrences;
    }

    private static LocalDateTime advance(LocalDateTime dateTime, RecurrenceRule rule) {
        return switch (rule.getUnit()) {
            case DAY -> dateTime.plusDays(rule.getIntervalCount());
            case WEEK -> dateTime.plusWeeks(rule.getIntervalCount());
            case MONTH -> dateTime.plusMonths(rule.getIntervalCount());
        };
    }
}