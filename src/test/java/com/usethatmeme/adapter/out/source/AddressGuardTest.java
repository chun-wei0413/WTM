package com.usethatmeme.adapter.out.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.usethatmeme.application.collection.FetchRefusedException;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class AddressGuardTest {

    private static void check(String url) {
        AddressGuard.requireAllowed(URI.create(url), false);
    }

    @Test
    void refusesAddressesOnPrivateNetworks() {
        for (String url : new String[] {
                "http://127.0.0.1/", "http://localhost/", "http://10.1.2.3/", "http://192.168.0.5/x.jpg",
                "http://172.16.0.9/", "http://169.254.169.254/latest/meta-data/", "http://0.0.0.0/",
                "http://100.64.0.1/", "http://[::1]/", "http://[fd00::1]/", "http://[fe80::1]/"}) {
            assertThatThrownBy(() -> check(url)).as(url).isInstanceOf(FetchRefusedException.class);
        }
    }

    @Test
    void refusesWhatIsNotPlainWeb() {
        assertThatThrownBy(() -> check("file:///etc/passwd")).isInstanceOf(FetchRefusedException.class);
        assertThatThrownBy(() -> check("ftp://example.com/a.jpg")).isInstanceOf(FetchRefusedException.class);
        assertThatThrownBy(() -> check("https://user:secret@example.com/a.jpg")).isInstanceOf(FetchRefusedException.class);
        assertThatThrownBy(() -> check("https://example.com:6379/")).isInstanceOf(FetchRefusedException.class);
        assertThatThrownBy(() -> check("/just/a/path")).isInstanceOf(FetchRefusedException.class);
    }

    @Test
    void anAddressThatCannotBeResolvedIsRefused() {
        assertThatThrownBy(() -> check("http://this-host-does-not-exist.invalid/"))
                .isInstanceOf(FetchRefusedException.class);
    }

    @Test
    void publicAddressesPass() {
        assertThatCode(() -> check("http://8.8.8.8/")).doesNotThrowAnyException();
        assertThatCode(() -> check("https://1.1.1.1:443/x.jpg")).doesNotThrowAnyException();
    }

    @Test
    void privateAddressesCanBeAllowedForTests() {
        assertThatCode(() -> AddressGuard.requireAllowed(URI.create("http://127.0.0.1:8123/x"), true))
                .doesNotThrowAnyException();
        // even then, only web addresses without credentials
        assertThatThrownBy(() -> AddressGuard.requireAllowed(URI.create("file:///x"), true))
                .isInstanceOf(FetchRefusedException.class);
    }

    @Test
    void classifiesAddresses() throws Exception {
        assertThat(AddressGuard.isPrivate(InetAddress.getByName("203.0.113.9"))).isFalse();
        assertThat(AddressGuard.isPrivate(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(AddressGuard.isPrivate(InetAddress.getByName("100.128.0.1"))).isFalse();
        assertThat(AddressGuard.isPrivate(InetAddress.getByName("255.255.255.255"))).isTrue();
    }
}
