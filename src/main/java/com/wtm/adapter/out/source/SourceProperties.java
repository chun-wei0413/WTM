package com.wtm.adapter.out.source;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * How the application behaves towards other websites when it is asked to download a picture from one.
 */
@ConfigurationProperties("wtm.sources")
public record SourceProperties(
        /** Sent with every request so site owners can tell who is asking. */
        @DefaultValue("wtm-personal-collector/0.1 (personal, non-commercial)") String userAgent,
        /** Optional e-mail or address added to the user agent; Wikimedia asks for one. */
        @DefaultValue("") String contact,
        /** The shortest wait between two requests to the same site (a robots.txt Crawl-delay can ask for more). */
        @DefaultValue("PT2S") Duration minDelay,
        @DefaultValue("PT20S") Duration timeout,
        @DefaultValue("15MB") DataSize maxImageSize,
        @DefaultValue("3MB") DataSize maxTextSize,
        /** Lets the collector reach loopback and private addresses. Only for tests; never switch it on otherwise. */
        @DefaultValue("false") boolean allowPrivateAddresses) {

    /** The product name robots.txt groups are matched against. */
    public String productToken() {
        return userAgent.split("[/\\s]", 2)[0];
    }

    /** The full user agent: the configured text plus the contact, when there is one. */
    public String fullUserAgent() {
        return contact == null || contact.isBlank() ? userAgent : userAgent + "; " + contact.strip();
    }
}
