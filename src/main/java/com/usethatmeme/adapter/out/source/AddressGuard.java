package com.usethatmeme.adapter.out.source;

import com.usethatmeme.application.collection.FetchRefusedException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Stops the collector from being talked into reading things on the machine or network it runs on
 * (server-side request forgery): an address that points at a private network is refused, even when
 * it comes in through a redirect or a host name that resolves to one.
 */
final class AddressGuard {

    private AddressGuard() {
    }

    static void requireAllowed(URI uri, boolean allowPrivate) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new FetchRefusedException("Only http and https addresses are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new FetchRefusedException("Addresses with a user name or password are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new FetchRefusedException("The address has no host name");
        }
        if (allowPrivate) {
            return;
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new FetchRefusedException("Only the standard web ports are allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FetchRefusedException("The host name could not be resolved: " + host, e);
        }
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                throw new FetchRefusedException("The address points at a private network and is not allowed");
            }
        }
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            return first == 0                                  // "this" network
                    || (first == 100 && (second & 0xC0) == 64) // 100.64.0.0/10, carrier-grade NAT
                    || (first == 169 && second == 254)         // link-local, including cloud metadata
                    || first >= 240;                           // reserved and broadcast
        }
        return (b[0] & 0xFE) == 0xFC;                          // fc00::/7, unique local IPv6
    }
}
