package com.wtm.application.collection;

/**
 * Where a collected picture came from, kept so it can be credited, checked and found again.
 *
 * @param sourceType which kind of source: IMGFLIP, WIKIMEDIA, PTT, UPLOAD, URL, INBOX …
 * @param sourceUrl the address of the image itself, when there is one
 * @param pageUrl the page it was found on, when there is one
 * @param attribution who made it or where it is credited, when known
 * @param licenseNote the licence or usage terms the source states, when known
 */
public record Origin(String sourceType, String sourceUrl, String pageUrl, String attribution, String licenseNote) {

    public static Origin of(String sourceType) {
        return new Origin(sourceType, null, null, null, null);
    }
}
