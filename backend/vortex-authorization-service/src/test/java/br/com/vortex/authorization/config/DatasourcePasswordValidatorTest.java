package br.com.vortex.authorization.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A suíte {@code @QuarkusTest} sobe no perfil test, onde o validador não faz nada; aqui ele recebe perfis e senha
 * direto, sem subir o Quarkus. A senha forte é gerada na hora: o prefixo traz letra e símbolo, e o hexadecimal (0-9 e
 * a-f) não consegue formar "admin" nem "password", porque não tem m, i, n, p, s, w, o nem r. Nenhum sorteio a deixa
 * fraca.
 */
class DatasourcePasswordValidatorTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * "secure_postgres_password" era o padrão da linha base, que ia na imagem; "vortex_auth_password" é a senha local
     * do docker-compose.auth.yml e dos perfis dev e leve.
     */
    @ParameterizedTest
    @ValueSource(strings = {"secure_postgres_password", "vortex_auth_password", "Vortex-2026", "OnlyLettersHere",
            "123456789012345", "Root-Admin-2026"})
    void prodRefusesAWeakPassword(String weak) {
        DatasourcePasswordValidator validator = validator(List.of("prod"), weak);

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void prodRefusesAnEmptyPassword() {
        DatasourcePasswordValidator validator = new DatasourcePasswordValidator(List.of("prod"), Optional.empty());

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void prodAcceptsAStrongPassword() {
        assertDoesNotThrow(() -> validator(List.of("prod"), strongPassword()).validate());
    }

    /** Sozinhos, os perfis de mensageria leem a linha base como o prod, então passam pela mesma checagem. */
    @ParameterizedTest
    @ValueSource(strings = {"kafka", "rabbitmq"})
    void aMessagingProfileAloneIsCheckedLikeProd(String profile) {
        DatasourcePasswordValidator validator = validator(List.of(profile), "vortex_auth_password");

        assertThrows(IllegalStateException.class, validator::validate);
    }

    /** Os perfis locais, com ou sem mensageria, usam a senha local de propósito. */
    @ParameterizedTest
    @ValueSource(strings = {"dev", "leve", "test", "leve,kafka", "dev,rabbitmq", "dev,test"})
    void localProfilesKeepTheirOwnPassword(String profiles) {
        assertDoesNotThrow(() -> validator(List.of(profiles.split(",")), "vortex_auth_password").validate());
    }

    private static DatasourcePasswordValidator validator(List<String> profiles, String password) {
        return new DatasourcePasswordValidator(profiles, Optional.of(password));
    }

    private static String strongPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "pg-" + HexFormat.of().formatHex(bytes);
    }
}
