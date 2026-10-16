package com.wtm.feedback.entity;

import com.wtm.feedback.entity.Report.Closing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Everything people said about one meme. Closing them is a decision about the meme as a whole, so the reports
 * are kept together: one open report per person, all closed at once, and the ones the rules dismissed can be
 * opened again.
 *
 * <p>The rules that look across memes (how many a person may report a day, how much a person's word counts) are
 * not here: no single case can guard them.
 */
public final class ReportCase {

    public static final int MAX_COMMENT_LENGTH = 300;

    private final UUID templateId;
    private final List<Report> reports;

    private ReportCase(UUID templateId, List<Report> reports) {
        this.templateId = templateId;
        this.reports = new ArrayList<>(reports);
    }

    public static ReportCase empty(UUID templateId) {
        return new ReportCase(templateId, List.of());
    }

    public static ReportCase restore(UUID templateId, List<Report> reports) {
        return new ReportCase(templateId, reports);
    }

    public UUID templateId() {
        return templateId;
    }

    /** Every report ever made about the meme, closed or not. */
    public List<Report> reports() {
        return Collections.unmodifiableList(reports);
    }

    /** The reports waiting for a decision, oldest first. */
    public List<Report> openReports() {
        return reports.stream().filter(Report::isOpen).sorted(java.util.Comparator.comparing(Report::createdAt)).toList();
    }

    public boolean hasOpenReports() {
        return reports.stream().anyMatch(Report::isOpen);
    }

    /**
     * Records what a person says about the meme. A second report from the same person replaces their open one.
     *
     * @throws IllegalArgumentException for a missing reason or a comment that is too long
     */
    public void submit(UUID userId, ReportReason reason, String comment, Instant now) {
        if (reason == null) {
            throw new IllegalArgumentException("A reason is required");
        }
        String text = comment == null ? "" : comment.strip();
        if (text.codePointCount(0, text.length()) > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("The comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        UUID id = UUID.randomUUID();
        for (int i = 0; i < reports.size(); i++) {
            Report earlier = reports.get(i);
            if (earlier.isOpen() && earlier.userId().equals(userId)) {
                id = earlier.id();
                reports.remove(i);
                break;
            }
        }
        reports.add(new Report(id, userId, reason, text, now, null));
    }

    /**
     * Closes every open report about the meme.
     *
     * @param automatic true when the rules decided, false when an administrator did
     * @return how many reports were closed
     */
    public int close(Resolution resolution, boolean automatic, String note, Instant now) {
        Closing closing = new Closing(resolution, automatic, note, now);
        int closed = 0;
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).isOpen()) {
                reports.set(i, reports.get(i).closedWith(closing));
                closed++;
            }
        }
        return closed;
    }

    /**
     * Opens again the reports the rules dismissed since the given time, except where the person has reported
     * the meme again in the meantime. A person's most recent dismissed report is the one that comes back.
     *
     * @return how many were opened
     */
    public int reopenAutomaticallyDismissed(Instant closedSince) {
        java.util.Map<UUID, Integer> latest = new java.util.HashMap<>();
        for (int i = 0; i < reports.size(); i++) {
            Closing closing = reports.get(i).closing();
            if (closing != null && closing.resolution() == Resolution.DISMISSED && closing.automatic()
                    && !closing.at().isBefore(closedSince)) {
                UUID user = reports.get(i).userId();
                Integer best = latest.get(user);
                if (best == null || reports.get(best).closing().at().isBefore(closing.at())) {
                    latest.put(user, i);
                }
            }
        }
        int reopened = 0;
        for (var entry : latest.entrySet()) {
            boolean hasOpen = reports.stream().anyMatch(r -> r.isOpen() && r.userId().equals(entry.getKey()));
            if (!hasOpen) {
                reports.set(entry.getValue(), reports.get(entry.getValue()).reopened());
                reopened++;
            }
        }
        return reopened;
    }
}
