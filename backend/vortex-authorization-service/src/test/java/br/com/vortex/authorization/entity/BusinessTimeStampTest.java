package br.com.vortex.authorization.entity;

import br.com.vortex.authorization.config.BusinessTime;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os carimbos das entidades saem no fuso de negócio, qualquer que seja o fuso da JVM (UTC no runner e no
 * container, America/Sao_Paulo na distro). O instante não muda; muda o deslocamento gravado.
 */
class BusinessTimeStampTest {

    @Test
    void roleCreatedAtCarriesTheBusinessOffset() {
        Role role = new Role();

        role.prePersist();

        assertNotNull(role.createdAt);
        assertEquals(BusinessTime.ZONE.getRules().getOffset(role.createdAt.toInstant()), role.createdAt.getOffset());
    }

    @Test
    void refreshTokenIsValidUntilItExpires() {
        RefreshToken token = new RefreshToken();
        token.revoked = false;

        token.expiresAt = OffsetDateTime.now(BusinessTime.ZONE).plusMinutes(5);
        assertTrue(token.isValid());

        token.expiresAt = OffsetDateTime.now(BusinessTime.ZONE).minusSeconds(1);
        assertFalse(token.isValid());
    }
}
