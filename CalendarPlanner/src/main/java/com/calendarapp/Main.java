package com.calendarapp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class Main {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter ALT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FLEX_DATE_TIME_T_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm");
    private static final DateTimeFormatter FLEX_DATE_TIME_SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d H:mm");
    private static final DateTimeFormatter FLEX_DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static void main(String[] args) throws IOException {
        EventStore store = new EventStore(Paths.get("data"));
        store.load();
        showStartupReminder(store);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("\n=== Calendar & Scheduler ===");
            System.out.println("1. Create event");
            System.out.println("2. Update event");
            System.out.println("3. Delete event");
            System.out.println("4. View events");
            System.out.println("5. Search events by date range");
            System.out.println("6. Advanced search & filter");
            System.out.println("7. Event statistics");
            System.out.println("8. Backup data");
            System.out.println("9. Restore data");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    createEvent(scanner, store);
                    break;
                case "2":
                    updateEvent(scanner, store);
                    break;
                case "3":
                    deleteEvent(scanner, store);
                    break;
                case "4":
                    viewEvents(scanner, store);
                    break;
                case "5":
                    searchEvents(scanner, store);
                    break;
                case "6":
                    advancedSearch(scanner, store);
                    break;
                case "7":
                    showStatistics(store);
                    break;
                case "8":
                    backup(scanner, store);
                    break;
                case "9":
                    restore(scanner, store);
                    break;
                case "0":
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
        }
        saveStore(store);
        System.out.println("Goodbye!");
    }

    private static void createEvent(Scanner scanner, EventStore store) throws IOException {
        if (!confirmAction(scanner, "Create event")) {
            return;
        }
        System.out.print("Title: ");
        String title = scanner.nextLine().trim();
        System.out.print("Description: ");
        String description = scanner.nextLine().trim();
        LocalDateTime start = promptDateTime(scanner,
                "Start (yyyy-MM-dd'T'HH:mm or yyyy-MM-dd HH:mm, date-only uses 00:00): ");
        LocalDateTime end = promptDateTime(scanner,
                "End (yyyy-MM-dd'T'HH:mm or yyyy-MM-dd HH:mm, date-only uses 00:00): ");

        RecurrenceInput recurrenceInput = promptRecurrenceInput(scanner);
        RecurrenceRule candidateRule = buildRecurrenceRule(-1, recurrenceInput);
        Event candidate = new Event(-1, title, description, start, end);
        if (store.hasConflict(-1, candidate, candidateRule)) {
            System.out.println("Cannot create event: it conflicts with an existing event.");
            return;
        }

        Event event = store.createEvent(title, description, start, end);
        if (recurrenceInput != null) {
            store.setRecurrence(buildRecurrenceRule(event.getId(), recurrenceInput));
        }
        Integer reminderMinutes = promptReminderMinutes(scanner, store, null);
        store.setReminderMinutes(event.getId(), reminderMinutes);
        if (saveStore(store)) {
            System.out.println("Event created with ID " + event.getId());
        }
    }

    private static void updateEvent(Scanner scanner, EventStore store) throws IOException {
        if (!confirmAction(scanner, "Update event")) {
            return;
        }
        listEventsSummary(store);
        Integer id = promptEventId(scanner, store, "update");
        if (id == null) {
            return;
        }
        Event event = store.findEvent(id).get();
        System.out.print("Title (" + event.getTitle() + "): ");
        String title = scanner.nextLine().trim();
        String newTitle = event.getTitle();
        if (!title.trim().isEmpty()) {
            newTitle = title;
        }
        System.out.print("Description (" + event.getDescription() + "): ");
        String description = scanner.nextLine().trim();
        String newDescription = event.getDescription();
        if (!description.trim().isEmpty()) {
            newDescription = description;
        }
        LocalDateTime start = promptOptionalDateTime(scanner, "Start (" + event.getStart().format(DATE_TIME_FORMAT) + "): ");
        LocalDateTime newStart = event.getStart();
        if (start != null) {
            newStart = start;
        }
        LocalDateTime end = promptOptionalDateTime(scanner, "End (" + event.getEnd().format(DATE_TIME_FORMAT) + "): ");
        LocalDateTime newEnd = event.getEnd();
        if (end != null) {
            newEnd = end;
        }

        RecurrenceRule existingRule = store.findRecurrence(event.getId()).orElse(null);
        RecurrenceInput recurrenceInput = null;
        System.out.print("Update recurrence? (y/n): ");
        if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.print("Remove recurrence? (y/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                existingRule = null;
            } else {
                recurrenceInput = promptRecurrenceInput(scanner);
                if (recurrenceInput == null) {
                    existingRule = null;
                }
            }
        }
        RecurrenceRule candidateRule = recurrenceInput == null ? existingRule
                : buildRecurrenceRule(event.getId(), recurrenceInput);
        Event candidate = new Event(event.getId(), newTitle, newDescription, newStart, newEnd);
        if (store.hasConflict(event.getId(), candidate, candidateRule)) {
            System.out.println("Cannot update event: it conflicts with an existing event.");
            return;
        }

        event.setTitle(newTitle);
        event.setDescription(newDescription);
        event.setStart(newStart);
        event.setEnd(newEnd);
        if (recurrenceInput != null) {
            store.setRecurrence(buildRecurrenceRule(event.getId(), recurrenceInput));
        } else if (existingRule == null) {
            store.clearRecurrence(event.getId());
        }
        Integer reminderMinutes = promptReminderMinutes(scanner, store, event.getId());
        store.setReminderMinutes(event.getId(), reminderMinutes);
        if (saveStore(store)) {
            System.out.println("Event updated.");
        }
    }

    private static void deleteEvent(Scanner scanner, EventStore store) throws IOException {
        if (!confirmAction(scanner, "Delete event")) {
            return;
        }
        listEventsSummary(store);
        Integer id = promptEventId(scanner, store, "delete");
        if (id == null) {
            return;
        }
        store.deleteEvent(id);
        if (saveStore(store)) {
            System.out.println("Event deleted.");
        }
    }

    private static void viewEvents(Scanner scanner, EventStore store) {
        if (!confirmAction(scanner, "View events")) {
            return;
        }
        System.out.println("View options:");
        System.out.println("1. Monthly calendar view");
        System.out.println("2. Daily list view");
        System.out.println("3. Weekly list view");
        System.out.println("4. Monthly list view");
        System.out.print("Choose: ");
        String option = scanner.nextLine().trim();

        switch (option) {
            case "1":
                viewMonthCalendar(scanner, store);
                break;
            case "2":
                viewDay(scanner, store);
                break;
            case "3":
                viewWeek(scanner, store);
                break;
            case "4":
                viewMonthList(scanner, store);
                break;
            default:
                System.out.println("Invalid option.");
                break;
        }
    }

    private static void viewDay(Scanner scanner, EventStore store) {
        LocalDate date = promptDate(scanner, "Date (yyyy-MM-dd): ");
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(date, date);
        System.out.println("=== Day of " + date + " ===");
        printOccurrencesForDate(occurrences, date);
    }

    private static void viewWeek(Scanner scanner, EventStore store) {
        LocalDate date = promptDate(scanner, "Any date within week (yyyy-MM-dd): ");
        LocalDate startOfWeek = date.with(DayOfWeek.SUNDAY);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(startOfWeek, endOfWeek);
        System.out.println("=== Week of " + startOfWeek + " ===");
        for (int i = 0; i < 7; i++) {
            LocalDate current = startOfWeek.plusDays(i);
            System.out.print(current.getDayOfWeek().toString().substring(0, 3) + " " +
                    String.format("%02d", current.getDayOfMonth()) + ": ");
            printOccurrencesForDateInline(occurrences, current);
        }
    }

    private static void viewMonthList(Scanner scanner, EventStore store) {
        YearMonth month = promptMonth(scanner);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        System.out.println("=== " + month.getMonth() + " " + month.getYear() + " ===");
        LocalDate current = start;
        while (!current.isAfter(end)) {
            System.out.print(current + ": ");
            printOccurrencesForDateInline(occurrences, current);
            current = current.plusDays(1);
        }
    }

    private static void viewMonthCalendar(Scanner scanner, EventStore store) {
        YearMonth month = promptMonth(scanner);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        System.out.println(month.getMonth() + " " + month.getYear());
        System.out.println("Su Mo Tu We Th Fr Sa");
        int startOffset = start.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < startOffset; i++) {
            System.out.print("   ");
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate current = month.atDay(day);
            boolean hasEvent = occurrences.containsKey(current);
            String marker = hasEvent ? "*" : " ";
            System.out.printf("%2d%s", day, marker);
            if ((day + startOffset) % 7 == 0) {
                System.out.println();
            } else {
                System.out.print(" ");
            }
        }
        System.out.println();
        for (Map.Entry<LocalDate, List<EventOccurrence>> entry : occurrences.entrySet()) {
            for (EventOccurrence occurrence : entry.getValue()) {
                System.out.println("* " + entry.getKey() + ": " + occurrence.getTitle() +
                        " (" + occurrence.getStart().format(TIME_FORMAT) + ")");
            }
        }
    }

    private static void searchEvents(Scanner scanner, EventStore store) {
        if (!confirmAction(scanner, "Search events")) {
            return;
        }
        LocalDate start = promptDate(scanner, "Start date (yyyy-MM-dd): ");
        LocalDate end = promptDate(scanner, "End date (yyyy-MM-dd): ");
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        System.out.println("=== Events between " + start + " and " + end + " ===");
        LocalDate current = start;
        while (!current.isAfter(end)) {
            System.out.print(current + ": ");
            printOccurrencesForDateInline(occurrences, current);
            current = current.plusDays(1);
        }
    }

    private static void advancedSearch(Scanner scanner, EventStore store) {
        if (!confirmAction(scanner, "Advanced search")) {
            return;
        }
        LocalDate start = promptDate(scanner, "Start date (yyyy-MM-dd): ");
        LocalDate end = promptDate(scanner, "End date (yyyy-MM-dd): ");
        System.out.print("Title contains (leave blank for any): ");
        String titleKeyword = scanner.nextLine().trim().toLowerCase();
        System.out.print("Description contains (leave blank for any): ");
        String descriptionKeyword = scanner.nextLine().trim().toLowerCase();
        System.out.print("Only recurring events? (y/n): ");
        boolean recurringOnly = scanner.nextLine().trim().equalsIgnoreCase("y");

        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        List<EventOccurrence> flattened = new ArrayList<>();
        for (List<EventOccurrence> dayOccurrences : occurrences.values()) {
            flattened.addAll(dayOccurrences);
        }
        flattened.sort(Comparator.comparing(EventOccurrence::getStart));

        System.out.println("=== Filtered results ===");
        for (EventOccurrence occurrence : flattened) {
            Event event = store.findEvent(occurrence.getEventId()).orElse(null);
            if (event == null) {
                continue;
            }
            if (!titleKeyword.isEmpty() && !event.getTitle().toLowerCase().contains(titleKeyword)) {
                continue;
            }
            if (!descriptionKeyword.isEmpty()
                    && !event.getDescription().toLowerCase().contains(descriptionKeyword)) {
                continue;
            }
            boolean recurring = store.findRecurrence(event.getId()).isPresent();
            if (recurringOnly && !recurring) {
                continue;
            }
            System.out.println(occurrence.getStart().toLocalDate() + ": " + event.getTitle()
                    + " (" + occurrence.getStart().format(TIME_FORMAT)
                    + " - " + occurrence.getEnd().format(TIME_FORMAT) + ")"
                    + (recurring ? " [Recurring]" : ""));
        }
    }

    private static void showStatistics(EventStore store) {
        List<Event> events = store.listEvents();
        int totalEvents = events.size();
        int totalRecurrences = store.listRecurrences().size();
        int totalReminders = store.reminderCount();

        LocalDate today = LocalDate.now();
        LocalDate rangeEnd = today.plusDays(30);
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(today, rangeEnd);
        List<EventOccurrence> allOccurrences = new ArrayList<>();
        for (List<EventOccurrence> dayOccurrences : occurrences.values()) {
            allOccurrences.addAll(dayOccurrences);
        }

        int upcomingCount = allOccurrences.size();
        long totalMinutes = 0;
        long longestMinutes = 0;
        String longestTitle = "-";
        int[] dayCounts = new int[7];
        for (EventOccurrence occurrence : allOccurrences) {
            long duration = Duration.between(occurrence.getStart(), occurrence.getEnd()).toMinutes();
            totalMinutes += duration;
            if (duration > longestMinutes) {
                longestMinutes = duration;
                longestTitle = occurrence.getTitle();
            }
            DayOfWeek day = occurrence.getStart().getDayOfWeek();
            dayCounts[day.getValue() % 7]++;
        }
        int busiestIndex = 0;
        for (int i = 1; i < dayCounts.length; i++) {
            if (dayCounts[i] > dayCounts[busiestIndex]) {
                busiestIndex = i;
            }
        }
        DayOfWeek busiestDay = DayOfWeek.of((busiestIndex == 0 ? 7 : busiestIndex));

        System.out.println("=== Statistics (next 30 days) ===");
        System.out.println("Total stored events: " + totalEvents);
        System.out.println("Recurring rules: " + totalRecurrences);
        System.out.println("Events with reminders: " + totalReminders);
        System.out.println("Upcoming occurrences: " + upcomingCount);
        System.out.println("Busiest day of week: " + busiestDay);
        if (upcomingCount > 0) {
            System.out.println("Average duration: " + (totalMinutes / upcomingCount) + " minutes");
        } else {
            System.out.println("Average duration: 0 minutes");
        }
        System.out.println("Longest event: " + longestTitle + " (" + longestMinutes + " minutes)");
    }

    private static void backup(Scanner scanner, EventStore store) throws IOException {
        if (!confirmAction(scanner, "Backup data")) {
            return;
        }
        System.out.print("Backup file path (e.g. backups/backup.txt): ");
        Path path = Paths.get(scanner.nextLine().trim());
        store.backup(path);
        System.out.println("Backup completed to " + path.toAbsolutePath());
    }

    private static void restore(Scanner scanner, EventStore store) throws IOException {
        if (!confirmAction(scanner, "Restore data")) {
            return;
        }
        System.out.print("Backup file path to restore: ");
        Path path = Paths.get(scanner.nextLine().trim());
        System.out.print("Replace existing data? (y/n): ");
        boolean replace = scanner.nextLine().trim().equalsIgnoreCase("y");
        store.restore(path, replace);
        System.out.println("Restore completed.");
    }

    private static void listEventsSummary(EventStore store) {
        List<Event> events = store.listEvents();
        if (events.isEmpty()) {
            System.out.println("No events found.");
            return;
        }
        System.out.println("Existing events:");
        for (Event event : events) {
            System.out.println(event.getId() + ": " + event.getTitle() + " (" + event.getStart() + ")");
        }
    }

    private static void printOccurrencesForDate(Map<LocalDate, List<EventOccurrence>> occurrences, LocalDate date) {
        List<EventOccurrence> list = occurrences.get(date);
        if (list == null || list.isEmpty()) {
            System.out.println("No events");
            return;
        }
        for (EventOccurrence occurrence : list) {
            System.out.println(occurrence.getTitle() + " (" + occurrence.getStart().format(TIME_FORMAT) + ")");
        }
    }

    private static void printOccurrencesForDateInline(Map<LocalDate, List<EventOccurrence>> occurrences, LocalDate date) {
        List<EventOccurrence> list = occurrences.get(date);
        if (list == null || list.isEmpty()) {
            System.out.println("No events");
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (EventOccurrence occurrence : list) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(occurrence.getTitle())
                    .append(" (")
                    .append(occurrence.getStart().format(TIME_FORMAT))
                    .append(")");
        }
        System.out.println(builder);
    }

    private static LocalDateTime promptDateTime(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            LocalDateTime parsed = parseDateTimeInput(input);
            if (parsed != null) {
                return parsed;
            }
            System.out.println("Invalid date time format.");
        }
    }

    private static LocalDateTime promptOptionalDateTime(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            if (input.trim().isEmpty()) {
                return null;
            }
            LocalDateTime parsed = parseDateTimeInput(input);
            if (parsed != null) {
                return parsed;
            }
            System.out.println("Invalid date time format.");
        }
    }

    private static LocalDateTime parseDateTimeInput(String input) {
        try {
            return LocalDateTime.parse(input, DATE_TIME_FORMAT);
        } catch (Exception ex) {
            // ignore and try alternate format
        }
        try {
            return LocalDateTime.parse(input, ALT_DATE_TIME_FORMAT);
        } catch (Exception ex) {
            // ignore and try date-only format
        }
        try {
            return LocalDate.parse(input, DATE_ONLY_FORMAT).atStartOfDay();
        } catch (Exception ex) {
            // ignore and try flexible date-only format
        }
        try {
            return LocalDate.parse(input, FLEX_DATE_ONLY_FORMAT).atStartOfDay();
        } catch (Exception ex) {
            // ignore and try flexible date time formats
        }
        try {
            return LocalDateTime.parse(input, FLEX_DATE_TIME_T_FORMAT);
        } catch (Exception ex) {
            // ignore and try flexible date time format with space
        }
        try {
            return LocalDateTime.parse(input, FLEX_DATE_TIME_SPACE_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer promptEventId(Scanner scanner, EventStore store, String actionLabel) {
        while (true) {
            System.out.print("Enter event ID to " + actionLabel + " (or press Enter to cancel): ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return null;
            }
            try {
                int id = Integer.parseInt(input);
                if (store.findEvent(id).isPresent()) {
                    return id;
                }
                System.out.println("Event ID not found.");
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a numeric event ID.");
            }
        }
    }

    private static LocalDate promptDate(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            try {
                return LocalDate.parse(input);
            } catch (Exception ex) {
                System.out.println("Invalid date format.");
            }
        }
    }

    private static YearMonth promptMonth(Scanner scanner) {
        while (true) {
            System.out.print("Month (yyyy-MM): ");
            String input = scanner.nextLine().trim();
            try {
                return YearMonth.parse(input);
            } catch (Exception ex) {
                System.out.println("Invalid month format.");
            }
        }
    }

    private static RecurrenceInput promptRecurrenceInput(Scanner scanner) {
        System.out.print("Recurring event? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            return null;
        }
        System.out.print("Interval (e.g. 1d, 2w, 1m): ");
        String interval = scanner.nextLine().trim();
        System.out.print("Repeat times (0 for until end date): ");
        int times = Integer.parseInt(scanner.nextLine().trim());
        String endDate = "0";
        if (times == 0) {
            while (true) {
                System.out.print("End date (yyyy-MM-dd or yyyy-MM-dd HH:mm): ");
                String input = scanner.nextLine().trim();
                LocalDate parsed = parseDateInput(input);
                if (parsed != null) {
                    endDate = parsed.toString();
                    break;
                }
                System.out.println("Invalid date format.");
            }
        }
        return new RecurrenceInput(interval, times, endDate);
    }

    private static RecurrenceRule buildRecurrenceRule(int eventId, RecurrenceInput input) {
        if (input == null) {
            return null;
        }
        return RecurrenceRule.parse(eventId, input.interval, input.times, input.endDate);
    }

    private static boolean saveStore(EventStore store) {
        try {
            store.save();
            return true;
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    private static boolean confirmAction(Scanner scanner, String actionName) {
        System.out.print(actionName + " (press Enter to continue, type cancel to go back): ");
        String input = scanner.nextLine().trim();
        return !input.equalsIgnoreCase("cancel");
    }

    private static LocalDate parseDateInput(String input) {
        try {
            return LocalDate.parse(input);
        } catch (Exception ex) {
            // ignore and try to parse date-time instead
        }
        LocalDateTime parsed = parseDateTimeInput(input);
        if (parsed != null) {
            return parsed.toLocalDate();
        }
        return null;
    }

    private static Integer promptReminderMinutes(Scanner scanner, EventStore store, Integer eventId) {
        String existing = "";
        if (eventId != null) {
            Optional<Integer> reminder = store.findReminderMinutes(eventId);
            if (reminder.isPresent()) {
                existing = reminder.get().toString();
            }
        }
        System.out.print("Reminder minutes before event"
                + (existing.isEmpty() ? "" : " (" + existing + ")") + " (blank for none): ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(input);
            if (minutes < 0) {
                System.out.println("Reminder minutes must be non-negative.");
                return null;
            }
            return minutes;
        } catch (NumberFormatException ex) {
            System.out.println("Reminder minutes must be a number.");
            return null;
        }
    }

    private static void showStartupReminder(EventStore store) {
        LocalDateTime now = LocalDateTime.now();
        EventOccurrence next = null;
        int reminderMinutes = 0;
        for (Event event : store.listEvents()) {
            RecurrenceRule rule = store.findRecurrence(event.getId()).orElse(null);
            LocalDate rangeStart = now.toLocalDate();
            LocalDate rangeEnd = rangeStart.plusDays(30);
            List<EventOccurrence> occurrences = EventTimeline.expandOccurrences(event, rule, rangeStart, rangeEnd);
            for (EventOccurrence occurrence : occurrences) {
                if (occurrence.getStart().isBefore(now)) {
                    continue;
                }
                Optional<Integer> minutes = store.findReminderMinutes(occurrence.getEventId());
                if (!minutes.isPresent()) {
                    continue;
                }
                if (next == null || occurrence.getStart().isBefore(next.getStart())) {
                    next = occurrence;
                    reminderMinutes = minutes.get();
                }
            }
        }
        if (next == null) {
            return;
        }
        Duration until = Duration.between(now, next.getStart());
        if (until.toMinutes() <= reminderMinutes) {
            System.out.println("Your next event is coming soon in " + formatDuration(until)
                    + ": " + next.getTitle());
        }
    }

    private static String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        }
        return minutes + "m";
    }

    private static class RecurrenceInput {
        private final String interval;
        private final int times;
        private final String endDate;

        private RecurrenceInput(String interval, int times, String endDate) {
            this.interval = interval;
            this.times = times;
            this.endDate = endDate;
        }
    }
}