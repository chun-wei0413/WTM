package com.wtm.application.report;

/**
 * Why someone thinks a meme's description is off.
 */
public enum ReportReason {
    /** The keywords do not match what the picture shows. */
    WRONG_TAGS,
    /** The meaning or the usage examples do not fit. */
    WRONG_MEANING,
    /** It is a photo, a screenshot or a joke told once, not a picture people send to answer someone. */
    NOT_A_MEME,
    /** The picture should not be in the library at all. */
    INAPPROPRIATE,
    OTHER;

    /** Whether the report is about the picture belonging in the library, which only a person decides. */
    public boolean questionsWhetherItBelongs() {
        return this == NOT_A_MEME || this == INAPPROPRIATE;
    }
}
