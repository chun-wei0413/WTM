package com.wtm.application.report;

/**
 * Why someone thinks a meme's description is off.
 */
public enum ReportReason {
    /** The keywords do not match what the picture shows. */
    WRONG_TAGS,
    /** The meaning or the usage examples do not fit. */
    WRONG_MEANING,
    /** The picture should not be in the library at all. */
    INAPPROPRIATE,
    OTHER
}
