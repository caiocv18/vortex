package br.com.vortex.application.config;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Senha de banco gerada na hora, como a EC2 faria com {@code openssl rand}, para nenhum valor fixo parecer credencial.
 *
 * <p>Em produção o {@link SecurityConfigurationValidator} recusa senha fraca, então a forma garante a regra sem
 * depender do sorteio: o prefixo traz letra e símbolo, e o hexadecimal (0-9 e a-f) não consegue formar "admin" nem
 * "password", porque não tem m, i, n, p, s, w, o nem r.
 * Um Base64 sorteado conteria "admin" de vez em quando e deixaria o teste instável.
 */
final class StrongTestPasswords {

    private static final SecureRandom RANDOM = new SecureRandom();

    private StrongTestPasswords() {
    }

    static String generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "pg-" + HexFormat.of().formatHex(bytes);
    }
}
