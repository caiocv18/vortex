package br.com.vortex.authorization.config;

import java.time.ZoneId;

/**
 * Fuso de negócio do Vortex. Cada {@code now()} do código de produção passa por aqui, para a mesma ação
 * gravar a mesma hora na distro (America/Sao_Paulo), no container e na EC2 (UTC).
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessTime() {
        // só constantes
    }
}
