package br.com.vortex.application.config;

import org.springframework.core.env.Environment;

/**
 * Onde a aplicação roda, decidido num lugar só a partir dos perfis ativos.
 *
 * <p>{@link Environment#matchesProfiles} compara o nome inteiro de cada perfil ativo. O {@code contains} que a
 * aplicação fazia na string de {@code spring.profiles.active} contava {@code preprod} como produção e não via
 * perfis que chegam por outro caminho, como um grupo de perfis.
 */
public final class DeploymentProfiles {

    static final String CLOUD = "cloud";
    private static final String PRODUCTION = "prd | prod | production | " + CLOUD;

    private DeploymentProfiles() {
    }

    /** Produção: o Docker com Oracle ({@code prd}) ou a demo pública ({@code cloud}). */
    public static boolean isProduction(Environment environment) {
        return environment.matchesProfiles(PRODUCTION);
    }

    /** A demo pública na EC2: só a API, com token, e o {@code /health} ficam expostos. */
    public static boolean isCloud(Environment environment) {
        return environment.matchesProfiles(CLOUD);
    }
}
