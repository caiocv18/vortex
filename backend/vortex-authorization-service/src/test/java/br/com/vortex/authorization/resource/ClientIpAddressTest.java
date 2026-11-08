package br.com.vortex.authorization.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unitário (sem Quarkus) do filtro de IP do AuthResource: o que vai para login_attempts.ip_address,
 * audit_logs.ip_address e para os eventos. Só IP literal passa, sempre na mesma forma; o resto vira null
 * e o AuthResource usa o par real da conexão.
 */
class ClientIpAddressTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "198.51.100.7, 198.51.100.7",
        "::1, 0:0:0:0:0:0:0:1",
        "[::1], 0:0:0:0:0:0:0:1",
        "0:0:0:0:0:0:0:1, 0:0:0:0:0:0:0:1",
        "2001:db8::1, 2001:db8:0:0:0:0:0:1",
        "fe80::1%1, fe80:0:0:0:0:0:0:1",
        "::ffff:198.51.100.7, 198.51.100.7"
    })
    @DisplayName("IP literals are kept in one canonical form (no IPv6 scope, IPv4-mapped as IPv4)")
    void ipLiteralsAreNormalized(String value, String expected) {
        assertEquals(expected, AuthResource.normalizeIpLiteral(value));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"localhost", "attacker.example", "unknown", "1.2.3.4.5", "1.2.3.4:80", "<script>"})
    @DisplayName("Anything that is not an IP literal is rejected, and host names are never resolved")
    void nonLiteralsAreRejected(String value) {
        assertNull(AuthResource.normalizeIpLiteral(value));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An oversized value is rejected before parsing (ip_address is varchar(255))")
    void oversizedValueIsRejected() {
        assertNull(AuthResource.normalizeIpLiteral("1".repeat(300)));
        assertNull(AuthResource.normalizeIpLiteral("2001:db8::1" + "%" + "a".repeat(AuthResource.MAX_IP_LITERAL_LENGTH)));
    }
}
