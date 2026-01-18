package com.calendarapp;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
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

public class CalendarPlannerGui {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter ALT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FLEX_DATE_TIME_T_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm");
    private static final DateTimeFormatter FLEX_DATE_TIME_SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d H:mm");
    private static final DateTimeFormatter FLEX_DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final EventStore store;
    private final DefaultTableModel tableModel;
    private final JTable eventTable;

    private final JTextField titleField = new JTextField(20);
    private final JTextField descriptionField = new JTextField(20);
    private final JTextField startField = new JTextField(20);
    private final JTextField endField = new JTextField(20);
    private final JTextField reminderField = new JTextField(8);
    private final JCheckBox recurrenceCheck = new JCheckBox("Enable recurrence");
    private final JTextField intervalField = new JTextField(6);
    private final JTextField timesField = new JTextField(4);
    private final JTextField recurrenceEndField = new JTextField(10);

    private final JComboBox<String> monthSelector = new JComboBox<>();
    private final JComboBox<Integer> yearSelector = new JComboBox<>();
    private final JTextArea calendarArea = new JTextArea(12, 30);
    private final JTextField searchStartField = new JTextField(10);
    private final JTextField searchEndField = new JTextField(10);
    private final JTextField searchTitleField = new JTextField(12);
    private final JTextField searchDescriptionField = new JTextField(12);
    private final JCheckBox searchRecurringOnly = new JCheckBox("Only recurring");
    private final DefaultTableModel searchTableModel = new DefaultTableModel(
            new Object[] {"Date", "Event ID", "Title", "Start", "End", "Recurring"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable searchTable = new JTable(searchTableModel);
    private final JTextArea statsArea = new JTextArea(12, 30);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CalendarPlannerGui().show());
    }

    public CalendarPlannerGui() {
        store = new EventStore(Paths.get("data"));
        loadStore();

        tableModel = new DefaultTableModel(new Object[] {"ID", "Title", "Start", "End"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        eventTable = new JTable(tableModel);
        eventTable.setPreferredScrollableViewportSize(new Dimension(520, 200));
        refreshTable();
    }

    private void show() {
        JFrame frame = new JFrame("Calendar Planner");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Events", buildEventsPanel());
        tabs.add("Calendar", buildCalendarPanel());
        tabs.add("Search", buildSearchPanel());
        tabs.add("Statistics", buildStatisticsPanel());

        frame.add(tabs, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        showWelcomeMessage();
        showReminderNotification();
    }

    private JPanel buildEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addField(form, gbc, row++, "Title", titleField);
        addField(form, gbc, row++, "Description", descriptionField);
        addField(form, gbc, row++, "Start (yyyy-MM-dd HH:mm)", startField);
        addField(form, gbc, row++, "End (yyyy-MM-dd HH:mm)", endField);
        addField(form, gbc, row++, "Reminder (minutes before)", reminderField);

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        form.add(recurrenceCheck, gbc);
        gbc.gridwidth = 1;

        addField(form, gbc, row++, "Interval (e.g. 1d, 1w)", intervalField);
        addField(form, gbc, row++, "Repeat times (0 for end date)", timesField);
        addField(form, gbc, row++, "Recurrence end date", recurrenceEndField);

        JButton createButton = new JButton("Create");
        JButton updateButton = new JButton("Update");
        JButton deleteButton = new JButton("Delete");
        JButton clearButton = new JButton("Clear");

        createButton.addActionListener(event -> createEvent());
        updateButton.addActionListener(event -> updateEvent());
        deleteButton.addActionListener(event -> deleteEvent());
        clearButton.addActionListener(event -> clearForm());

        JPanel buttonRow = new JPanel();
        buttonRow.add(createButton);
        buttonRow.add(updateButton);
        buttonRow.add(deleteButton);
        buttonRow.add(clearButton);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Events"));
        tablePanel.add(new JScrollPane(eventTable), BorderLayout.CENTER);

        eventTable.getSelectionModel().addListSelectionListener(event -> populateFormFromSelection());

        panel.add(form, BorderLayout.WEST);
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCalendarPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        for (int i = 0; i < 12; i++) {
            monthSelector.addItem(YearMonth.now().withMonth(i + 1).getMonth().name());
        }
        monthSelector.setSelectedIndex(YearMonth.now().getMonthValue() - 1);
        int currentYear = YearMonth.now().getYear();
        for (int year = currentYear - 5; year <= currentYear + 5; year++) {
            yearSelector.addItem(year);
        }
        yearSelector.setSelectedItem(currentYear);
        JButton renderButton = new JButton("Render calendar");
        renderButton.addActionListener(event -> renderCalendar());

        JPanel top = new JPanel();
        top.add(new JLabel("Month:"));
        top.add(monthSelector);
        top.add(new JLabel("Year:"));
        top.add(yearSelector);
        top.add(renderButton);

        calendarArea.setEditable(false);
        calendarArea.setFont(calendarArea.getFont().deriveFont(14f));
        renderCalendar();

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(calendarArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel filters = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addField(filters, gbc, row++, "Start date (yyyy-MM-dd)", searchStartField);
        addField(filters, gbc, row++, "End date (yyyy-MM-dd)", searchEndField);
        addField(filters, gbc, row++, "Title contains", searchTitleField);
        addField(filters, gbc, row++, "Description contains", searchDescriptionField);
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        filters.add(searchRecurringOnly, gbc);
        gbc.gridwidth = 1;

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(event -> runSearch());
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> clearSearchFilters());

        JPanel buttons = new JPanel();
        buttons.add(searchButton);
        buttons.add(clearButton);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Results"));
        tablePanel.add(new JScrollPane(searchTable), BorderLayout.CENTER);

        panel.add(filters, BorderLayout.WEST);
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatisticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        statsArea.setEditable(false);
        statsArea.setFont(statsArea.getFont().deriveFont(14f));
        JButton refreshButton = new JButton("Refresh statistics");
        refreshButton.addActionListener(event -> refreshStatistics());

        panel.add(new JScrollPane(statsArea), BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);
        refreshStatistics();
        return panel;
    }

    private void renderCalendar() {
        int monthIndex = monthSelector.getSelectedIndex() + 1;
        int year = (int) yearSelector.getSelectedItem();
        YearMonth month = YearMonth.of(year, monthIndex);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        java.util.Set<LocalDate> highlightedDates = buildHighlightedDates(start, end);

        StringBuilder builder = new StringBuilder();
        builder.append(month.getMonth()).append(" ").append(month.getYear()).append("\n");
        builder.append("Su Mo Tu We Th Fr Sa\n");
        int startOffset = start.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < startOffset; i++) {
            builder.append("   ");
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate current = month.atDay(day);
            boolean hasEvent = highlightedDates.contains(current);
            String marker = hasEvent ? "*" : " ";
            builder.append(String.format("%2d%s", day, marker));
            if ((day + startOffset) % 7 == 0) {
                builder.append("\n");
            } else {
                builder.append(" ");
            }
        }
        builder.append("\n\nEvents:\n");
        for (Map.Entry<LocalDate, List<EventOccurrence>> entry : occurrences.entrySet()) {
            for (EventOccurrence occurrence : entry.getValue()) {
                LocalDate startDate = occurrence.getStart().toLocalDate();
                LocalDate endDate = occurrence.getEnd().toLocalDate();
                builder.append("* ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(occurrence.getTitle())
                        .append(" (")
                        .append(occurrence.getStart().format(TIME_FORMAT))
                        .append(" - ")
                        .append(occurrence.getEnd().format(TIME_FORMAT))
                        .append(")");
                if (!startDate.equals(endDate)) {
                    builder.append(" [")
                            .append(startDate)
                            .append(" to ")
                            .append(endDate)
                            .append("]");
                }
                builder.append("\n");
            }
        }
        calendarArea.setText(builder.toString());
    }

    private java.util.Set<LocalDate> buildHighlightedDates(LocalDate monthStart, LocalDate monthEnd) {
        java.util.Set<LocalDate> highlighted = new java.util.HashSet<>();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(monthStart, monthEnd);
        for (List<EventOccurrence> dayOccurrences : occurrences.values()) {
            for (EventOccurrence occurrence : dayOccurrences) {
                LocalDate eventStart = occurrence.getStart().toLocalDate();
                LocalDate eventEnd = occurrence.getEnd().toLocalDate();
                LocalDate cursor = eventStart;
                while (!cursor.isAfter(eventEnd)) {
                    if (!cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd)) {
                        highlighted.add(cursor);
                    }
                    cursor = cursor.plusDays(1);
                }
            }
        }
        return highlighted;
    }

    private void createEvent() {
        try {
            String title = titleField.getText().trim();
            String description = descriptionField.getText().trim();
            LocalDateTime start = parseDateTimeInput(startField.getText().trim());
            LocalDateTime end = parseDateTimeInput(endField.getText().trim());
            RecurrenceInput recurrenceInput = readRecurrenceInput();
            RecurrenceRule candidateRule = buildRecurrenceRule(-1, recurrenceInput);
            Event candidate = new Event(-1, title, description, start, end);
            if (store.hasConflict(-1, candidate, candidateRule)) {
                showError("This event conflicts with an existing event.");
                return;
            }

            Event event = store.createEvent(title, description, start, end);
            if (recurrenceInput != null) {
                store.setRecurrence(buildRecurrenceRule(event.getId(), recurrenceInput));
            } else {
                store.clearRecurrence(event.getId());
            }
            Integer reminderMinutes = parseReminderMinutes(reminderField.getText().trim());
            store.setReminderMinutes(event.getId(), reminderMinutes);
            saveStore();
            refreshTable();
            clearForm();
            renderCalendar();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void updateEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            showError("Select an event first.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Event event = store.findEvent(id).orElse(null);
        if (event == null) {
            showError("Event not found.");
            return;
        }
        try {
            String title = titleField.getText().trim();
            String description = descriptionField.getText().trim();
            LocalDateTime start = parseDateTimeInput(startField.getText().trim());
            LocalDateTime end = parseDateTimeInput(endField.getText().trim());
            RecurrenceInput recurrenceInput = readRecurrenceInput();
            RecurrenceRule candidateRule = buildRecurrenceRule(event.getId(), recurrenceInput);
            Event candidate = new Event(event.getId(), title, description, start, end);
            if (store.hasConflict(event.getId(), candidate, candidateRule)) {
                showError("This update conflicts with an existing event.");
                return;
            }
            event.setTitle(title);
            event.setDescription(description);
            event.setStart(start);
            event.setEnd(end);
            if (recurrenceInput != null) {
                store.setRecurrence(buildRecurrenceRule(event.getId(), recurrenceInput));
            } else {
                store.clearRecurrence(event.getId());
            }
            Integer reminderMinutes = parseReminderMinutes(reminderField.getText().trim());
            store.setReminderMinutes(event.getId(), reminderMinutes);
            saveStore();
            refreshTable();
            renderCalendar();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            showError("Select an event first.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        store.deleteEvent(id);
        saveStore();
        refreshTable();
        clearForm();
        renderCalendar();
    }

    private void populateFormFromSelection() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Event event = store.findEvent(id).orElse(null);
        if (event == null) {
            return;
        }
        titleField.setText(event.getTitle());
        descriptionField.setText(event.getDescription());
        startField.setText(event.getStart().format(DATE_TIME_FORMAT));
        endField.setText(event.getEnd().format(DATE_TIME_FORMAT));
        recurrenceCheck.setSelected(store.findRecurrence(id).isPresent());
        Optional<Integer> reminderMinutes = store.findReminderMinutes(id);
        reminderField.setText(reminderMinutes.isPresent() ? String.valueOf(reminderMinutes.get()) : "");
        populateRecurrenceFields(id);
    }

    private void clearForm() {
        titleField.setText("");
        descriptionField.setText("");
        startField.setText("");
        endField.setText("");
        recurrenceCheck.setSelected(false);
        intervalField.setText("");
        timesField.setText("");
        recurrenceEndField.setText("");
        reminderField.setText("");
        eventTable.clearSelection();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Event event : store.listEvents()) {
            tableModel.addRow(new Object[] {
                    event.getId(),
                    event.getTitle(),
                    event.getStart().format(DATE_TIME_FORMAT),
                    event.getEnd().format(DATE_TIME_FORMAT)
            });
        }
    }

    private void runSearch() {
        searchTableModel.setRowCount(0);
        LocalDate startDate = parseDateInput(searchStartField.getText().trim());
        LocalDate endDate = parseDateInput(searchEndField.getText().trim());
        if (startDate == null || endDate == null) {
            showError("Enter both start and end dates for searching.");
            return;
        }
        if (endDate.isBefore(startDate)) {
            showError("End date must be after start date.");
            return;
        }
        String titleKeyword = searchTitleField.getText().trim().toLowerCase();
        String descriptionKeyword = searchDescriptionField.getText().trim().toLowerCase();
        boolean recurringOnly = searchRecurringOnly.isSelected();

        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(startDate, endDate);
        List<EventOccurrence> flattened = new ArrayList<>();
        for (List<EventOccurrence> dayOccurrences : occurrences.values()) {
            flattened.addAll(dayOccurrences);
        }
        flattened.sort(Comparator.comparing(EventOccurrence::getStart));

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
            searchTableModel.addRow(new Object[] {
                    occurrence.getStart().toLocalDate(),
                    event.getId(),
                    event.getTitle(),
                    occurrence.getStart().format(DATE_TIME_FORMAT),
                    occurrence.getEnd().format(DATE_TIME_FORMAT),
                    recurring ? "Yes" : "No"
            });
        }
    }

    private void clearSearchFilters() {
        searchStartField.setText("");
        searchEndField.setText("");
        searchTitleField.setText("");
        searchDescriptionField.setText("");
        searchRecurringOnly.setSelected(false);
        searchTableModel.setRowCount(0);
    }

    private void refreshStatistics() {
        statsArea.setText(buildStatisticsText());
    }

    private void saveStore() {
        try {
            store.save();
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadStore() {
        try {
            store.load();
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String labelText, JTextField field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(labelText + ":"), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private LocalDateTime parseDateTimeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Date/time is required.");
        }
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
            throw new IllegalArgumentException("Invalid date/time format.");
        }
    }

    private LocalDate parseDateInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(input);
        } catch (Exception ex) {
            // ignore and try to parse date-time instead
        }
        try {
            return LocalDate.parse(input, FLEX_DATE_ONLY_FORMAT);
        } catch (Exception ex) {
            // ignore and try to parse date-time instead
        }
        LocalDateTime parsed = null;
        try {
            parsed = parseDateTimeInput(input);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return parsed.toLocalDate();
    }

    private int parseInteger(String input, String errorMessage) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Calendar Planner", JOptionPane.ERROR_MESSAGE);
    }

    private void showWelcomeMessage() {
        JOptionPane.showMessageDialog(null,
                "Welcome to the Calendar Planner App!",
                "Calendar Planner",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showReminderNotification() {
        LocalDateTime now = LocalDateTime.now();
        EventOccurrence next = null;
        int reminderMinutes = 0;
        LocalDate rangeStart = now.toLocalDate();
        LocalDate rangeEnd = rangeStart.plusDays(90);
        for (Event event : store.listEvents()) {
            RecurrenceRule rule = store.findRecurrence(event.getId()).orElse(null);
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
            JOptionPane.showMessageDialog(null,
                    "Your next event is coming soon in " + formatDuration(until)
                            + ": " + next.getTitle(),
                    "Reminder",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        }
        return minutes + "m";
    }

    private String buildStatisticsText() {
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

        StringBuilder builder = new StringBuilder();
        builder.append("Statistics (next 30 days for occurrences)\n\n");
        builder.append("Total stored events: ").append(totalEvents).append("\n");
        builder.append("Recurring rules: ").append(totalRecurrences).append("\n");
        builder.append("Events with reminders: ").append(totalReminders).append("\n");
        builder.append("Upcoming occurrences: ").append(upcomingCount).append("\n");
        builder.append("Busiest day of week: ").append(busiestDay).append("\n");
        if (upcomingCount > 0) {
            builder.append("Average duration: ").append(totalMinutes / upcomingCount).append(" minutes\n");
        } else {
            builder.append("Average duration: 0 minutes\n");
        }
        builder.append("Longest event: ").append(longestTitle)
                .append(" (").append(longestMinutes).append(" minutes)\n");
        return builder.toString();
    }

    private Integer parseReminderMinutes(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(input.trim());
            if (minutes < 0) {
                throw new IllegalArgumentException("Reminder minutes must be non-negative.");
            }
            return minutes;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Reminder minutes must be a number.");
        }
    }

    private RecurrenceInput readRecurrenceInput() {
        if (!recurrenceCheck.isSelected()) {
            return null;
        }
        String interval = intervalField.getText().trim();
        if (interval.isEmpty()) {
            throw new IllegalArgumentException("Interval is required for recurrence.");
        }
        int times = parseInteger(timesField.getText().trim(), "Repeat times must be a number.");
        String endDate = "0";
        if (times == 0) {
            LocalDate parsed = parseDateInput(recurrenceEndField.getText().trim());
            if (parsed == null) {
                throw new IllegalArgumentException("Recurrence end date is invalid.");
            }
            endDate = parsed.toString();
        }
        return new RecurrenceInput(interval, times, endDate);
    }

    private RecurrenceRule buildRecurrenceRule(int eventId, RecurrenceInput input) {
        if (input == null) {
            return null;
        }
        return RecurrenceRule.parse(eventId, input.interval, input.times, input.endDate);
    }

    private void populateRecurrenceFields(int eventId) {
        RecurrenceRule rule = store.findRecurrence(eventId).orElse(null);
        if (rule == null) {
            intervalField.setText("");
            timesField.setText("");
            recurrenceEndField.setText("");
            return;
        }
        intervalField.setText(rule.toIntervalString());
        timesField.setText(String.valueOf(rule.getTimes()));
        recurrenceEndField.setText(rule.getEndDate() == null ? "" : rule.getEndDate().toString());
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