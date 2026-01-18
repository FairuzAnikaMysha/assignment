package com.calendarapp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.temporal.ChronoUnit;

public class EventStore {
    private final Path dataDirectory;
    private final Path eventFile;
    private final Path recurrenceFile;
    private final Path reminderFile;
    private final Map<Integer, Event> events = new HashMap<>();
    private final Map<Integer, RecurrenceRule> recurrences = new HashMap<>();
    private final Map<Integer, Integer> reminders = new HashMap<>();
    private int nextId = 1;

    public EventStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.eventFile = dataDirectory.resolve("event.csv");
        this.recurrenceFile = dataDirectory.resolve("recurrent.csv");
        this.reminderFile = dataDirectory.resolve("reminder.csv");
    }

    public void load() throws IOException {
        Files.createDirectories(dataDirectory);
        events.clear();
        recurrences.clear();
        reminders.clear();
        nextId = 1;

        if (Files.exists(eventFile)) {
            List<String> lines = Files.readAllLines(eventFile);
            for (int i = 1; i < lines.size(); i++) {
                List<String> fields = CsvUtil.parseLine(lines.get(i));
                if (fields.size() < 5) {
                    continue;
                }
                int id = Integer.parseInt(fields.get(0));
                Event event = new Event(
                        id,
                        fields.get(1),
                        fields.get(2),
                        LocalDateTime.parse(fields.get(3)),
                        LocalDateTime.parse(fields.get(4))
                );
                events.put(id, event);
                nextId = Math.max(nextId, id + 1);
            }
        }

        if (Files.exists(recurrenceFile)) {
            List<String> lines = Files.readAllLines(recurrenceFile);
            for (int i = 1; i < lines.size(); i++) {
                List<String> fields = CsvUtil.parseLine(lines.get(i));
                if (fields.size() < 4) {
                    continue;
                }
                int eventId = Integer.parseInt(fields.get(0));
                RecurrenceRule rule = RecurrenceRule.parse(
                        eventId,
                        fields.get(1),
                        Integer.parseInt(fields.get(2)),
                        fields.get(3)
                );
                recurrences.put(eventId, rule);
            }
        }

        if (Files.exists(reminderFile)) {
            List<String> lines = Files.readAllLines(reminderFile);
            for (int i = 1; i < lines.size(); i++) {
                List<String> fields = CsvUtil.parseLine(lines.get(i));
                if (fields.size() < 2) {
                    continue;
                }
                int eventId = Integer.parseInt(fields.get(0));
                int minutes = Integer.parseInt(fields.get(1));
                reminders.put(eventId, minutes);
            }
        }
    }

    public Event createEvent(String title, String description, LocalDateTime start, LocalDateTime end) {
        Event event = new Event(nextId++, title, description, start, end);
        events.put(event.getId(), event);
        return event;
    }

    public Optional<Event> findEvent(int id) {
        return Optional.ofNullable(events.get(id));
    }

    public List<Event> listEvents() {
        List<Event> list = new ArrayList<>(events.values());
        list.sort(Comparator.comparing(Event::getStart));
        return list;
    }

    public void deleteEvent(int id) {
        events.remove(id);
        recurrences.remove(id);
        reminders.remove(id);
    }

    public void setRecurrence(RecurrenceRule rule) {
        if (rule == null) {
            return;
        }
        recurrences.put(rule.getEventId(), rule);
    }

    public void clearRecurrence(int eventId) {
        recurrences.remove(eventId);
    }

    public Optional<RecurrenceRule> findRecurrence(int eventId) {
        return Optional.ofNullable(recurrences.get(eventId));
    }

    public List<RecurrenceRule> listRecurrences() {
        return new ArrayList<>(recurrences.values());
    }

    public void setReminderMinutes(int eventId, Integer minutes) {
        if (minutes == null) {
            reminders.remove(eventId);
            return;
        }
        reminders.put(eventId, minutes);
    }

    public Optional<Integer> findReminderMinutes(int eventId) {
        return Optional.ofNullable(reminders.get(eventId));
    }

    public int reminderCount() {
        return reminders.size();
    }

    public void save() throws IOException {
        Files.createDirectories(dataDirectory);
        writeEventsFile();
        writeRecurrenceFile();
        writeReminderFile();
    }

    private void writeEventsFile() throws IOException {
        Path tempFile = eventFile.resolveSibling("event.csv.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            writer.write("eventId,title,description,startDateTime,endDateTime");
            writer.newLine();
            for (Event event : listEvents()) {
                writer.write(event.getId() + "," +
                        CsvUtil.toCsvField(event.getTitle()) + "," +
                        CsvUtil.toCsvField(event.getDescription()) + "," +
                        event.getStart() + "," +
                        event.getEnd());
                writer.newLine();
            }
        }
        moveTempFile(tempFile, eventFile);
    }

    private void writeRecurrenceFile() throws IOException {
        Path tempFile = recurrenceFile.resolveSibling("recurrent.csv.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            writer.write("eventId,recurrentInterval,recurrentTimes,recurrentEndDate");
            writer.newLine();
            for (RecurrenceRule rule : listRecurrences()) {
                writer.write(rule.getEventId() + "," +
                        rule.toIntervalString() + "," +
                        rule.getTimes() + "," +
                        (rule.getEndDate() == null ? 0 : rule.getEndDate()));
                writer.newLine();
            }
        }
        moveTempFile(tempFile, recurrenceFile);
    }

    private void writeReminderFile() throws IOException {
        Path tempFile = reminderFile.resolveSibling("reminder.csv.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            writer.write("eventId,minutesBefore");
            writer.newLine();
            for (Map.Entry<Integer, Integer> entry : reminders.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue());
                writer.newLine();
            }
        }
        moveTempFile(tempFile, reminderFile);
    }

    private void moveTempFile(Path tempFile, Path targetFile) throws IOException {
        try {
            Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.FileSystemException ex) {
            throw new IOException("Unable to save " + targetFile.getFileName()
                    + ". Please close any program using the file and try again.", ex);
        }
    }

    public void backup(Path backupFile) throws IOException {
        Files.createDirectories(backupFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(backupFile)) {
            writer.write("#EVENTS");
            writer.newLine();
            if (Files.exists(eventFile)) {
                for (String line : Files.readAllLines(eventFile)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.write("#RECURRENCES");
            writer.newLine();
            if (Files.exists(recurrenceFile)) {
                for (String line : Files.readAllLines(recurrenceFile)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.write("#REMINDERS");
            writer.newLine();
            if (Files.exists(reminderFile)) {
                for (String line : Files.readAllLines(reminderFile)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }

    public void restore(Path backupFile, boolean replace) throws IOException {
        List<String> lines = Files.readAllLines(backupFile);
        List<String> eventLines = new ArrayList<>();
        List<String> recurrenceLines = new ArrayList<>();
        List<String> reminderLines = new ArrayList<>();
        boolean inEvents = false;
        boolean inRecurrences = false;
        boolean inReminders = false;
        for (String line : lines) {
            if (line.equals("#EVENTS")) {
                inEvents = true;
                inRecurrences = false;
                inReminders = false;
                continue;
            }
            if (line.equals("#RECURRENCES")) {
                inEvents = false;
                inRecurrences = true;
                inReminders = false;
                continue;
            }
            if (line.equals("#REMINDERS")) {
                inEvents = false;
                inRecurrences = false;
                inReminders = true;
                continue;
            }
            if (inEvents) {
                eventLines.add(line);
            } else if (inRecurrences) {
                recurrenceLines.add(line);
            } else if (inReminders) {
                reminderLines.add(line);
            }
        }

        if (replace) {
            Files.createDirectories(dataDirectory);
            Files.write(eventFile, eventLines);
            Files.write(recurrenceFile, recurrenceLines);
            Files.write(reminderFile, reminderLines);
        } else {
            Files.createDirectories(dataDirectory);
            appendLines(eventFile, eventLines);
            appendLines(recurrenceFile, recurrenceLines);
            appendLines(reminderFile, reminderLines);
        }
        load();
    }

    private void appendLines(Path file, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        if (!Files.exists(file)) {
            Files.write(file, lines);
            return;
        }
        List<String> existing = Files.readAllLines(file);
        if (existing.isEmpty()) {
            Files.write(file, lines);
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.APPEND)) {
            int startIndex = 0;
            if (!existing.isEmpty() && !lines.isEmpty() && existing.get(0).equals(lines.get(0))) {
                startIndex = 1;
            }
            for (int i = startIndex; i < lines.size(); i++) {
                writer.newLine();
                writer.write(lines.get(i));
            }
        }
    }

    public Map<LocalDate, List<EventOccurrence>> occurrencesBetween(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<EventOccurrence>> result = new HashMap<>();
        for (Event event : events.values()) {
            RecurrenceRule rule = recurrences.get(event.getId());
            List<EventOccurrence> occurrences = EventTimeline.expandOccurrences(event, rule, startDate, endDate);
            for (EventOccurrence occurrence : occurrences) {
                LocalDate date = occurrence.getStart().toLocalDate();
                result.computeIfAbsent(date, key -> new ArrayList<>()).add(occurrence);
            }
        }
        for (List<EventOccurrence> dayOccurrences : result.values()) {
            dayOccurrences.sort(Comparator.comparing(EventOccurrence::getStart));
        }
        return result;
    }

    public boolean hasConflict(int ignoreEventId, Event candidate, RecurrenceRule rule) {
        if (candidate == null) {
            return false;
        }
        LocalDate rangeStart = candidate.getStart().toLocalDate();
        LocalDate rangeEnd = calculateRangeEnd(
                candidate.getStart().toLocalDate(),
                candidate.getEnd().toLocalDate(),
                rule
        );
        Event tempEvent = new Event(candidate.getId(), candidate.getTitle(),
                candidate.getDescription(), candidate.getStart(), candidate.getEnd());
        List<EventOccurrence> candidateOccurrences = EventTimeline.expandOccurrences(
                tempEvent, rule, rangeStart, rangeEnd
        );

        for (Event existing : events.values()) {
            if (existing.getId() == ignoreEventId) {
                continue;
            }
            RecurrenceRule existingRule = recurrences.get(existing.getId());
            List<EventOccurrence> existingOccurrences = EventTimeline.expandOccurrences(
                    existing, existingRule, rangeStart, rangeEnd
            );
            for (EventOccurrence existingOccurrence : existingOccurrences) {
                for (EventOccurrence candidateOccurrence : candidateOccurrences) {
                    if (overlaps(existingOccurrence, candidateOccurrence)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean overlaps(EventOccurrence first, EventOccurrence second) {
        return first.getStart().isBefore(second.getEnd())
                && first.getEnd().isAfter(second.getStart());
    }

    private LocalDate calculateRangeEnd(LocalDate startDate, LocalDate endDate, RecurrenceRule rule) {
        LocalDate rangeEnd = endDate.isAfter(startDate) ? endDate : startDate;
        long durationDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (rule == null) {
            return rangeEnd;
        }
        LocalDate lastOccurrenceStart = startDate;
        if (rule.getTimes() > 0) {
            int occurrences = Math.max(1, rule.getTimes());
            lastOccurrenceStart = advanceDate(startDate, rule, occurrences - 1);
        }
        if (rule.getEndDate() != null && rule.getEndDate().isAfter(lastOccurrenceStart)) {
            lastOccurrenceStart = rule.getEndDate();
        }
        LocalDate lastOccurrenceEnd = durationDays > 0
                ? lastOccurrenceStart.plusDays(durationDays)
                : lastOccurrenceStart;
        if (lastOccurrenceEnd.isAfter(rangeEnd)) {
            rangeEnd = lastOccurrenceEnd;
        }
        return rangeEnd;
    }

    private LocalDate advanceDate(LocalDate startDate, RecurrenceRule rule, int steps) {
        if (steps <= 0) {
            return startDate;
        }
        if (rule.getUnit() == RecurrenceUnit.DAY) {
            return startDate.plusDays((long) rule.getIntervalCount() * steps);
        }
        if (rule.getUnit() == RecurrenceUnit.WEEK) {
            return startDate.plusWeeks((long) rule.getIntervalCount() * steps);
        }
        return startDate.plusMonths((long) rule.getIntervalCount() * steps);
    }
}