package com.memehub.application.port.out;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A place memes can be collected from. Discovery is lazy: the iterator only asks the site for the
 * next page of results when the previous ones have been used, so stopping early costs nothing.
 */
public interface MemeSourcePort {

    /** Short identifier used in requests, for example {@code IMGFLIP}. */
    String id();

    String displayName();

    String description();

    /** The settings this source understands, for showing in a form. */
    List<SourceOption> options();

    /**
     * @param options values for {@link #options()} by key; missing ones use their defaults
     * @throws com.memehub.application.collection.FetchRefusedException when the site cannot be read
     */
    Iterator<RemoteMeme> discover(Map<String, String> options);

    /**
     * @param imageUrl where to download the picture
     * @param pageUrl the page it was found on, or null
     * @param title a good name for it, or null when the source has none (then it is named after what it shows)
     * @param attribution who made it or where it is credited, or null
     * @param licenseNote the licence the source states, or null
     */
    record RemoteMeme(String imageUrl, String pageUrl, String title, String attribution, String licenseNote) {
    }

    record SourceOption(String key, String label, String defaultValue) {
    }
}
