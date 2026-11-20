package br.com.vortex.authorization.config;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Recusa subir com a senha do banco vazia ou fraca fora dos perfis locais.
 *
 * <p>Os perfis {@code dev}, {@code leve} e {@code test} têm senha própria no application.properties. Qualquer outra
 * combinação lê {@code POSTGRES_DB_PASSWORD} pela linha base: o {@code prod} da imagem publicada, mas também um
 * {@code kafka} ou um {@code rabbitmq} sozinho. Por isso a checagem vale sempre que nenhum perfil local está ativo, e
 * não só no {@code prod}: um perfil novo já nasce protegido, sem ninguém lembrar de incluí-lo numa lista.
 *
 * <p>A regra de senha fraca é a mesma do {@code SecurityConfigurationValidator} do serviço principal. A checagem roda
 * no {@link StartupEvent}, depois do Flyway. Sem a variável, o Quarkus não reclama: a senha do datasource é opcional, e
 * a expressão que não expande vira "sem senha". O PostgreSQL normalmente recusa a conexão antes; um banco que aceite
 * conexão sem senha passa pelo Flyway e para aqui, como senha vazia (os dois casos medidos em 17/11/2026).
 */
// @Singleton: só o observador do StartupEvent usa o bean, que não tem o construtor vazio do proxy de @ApplicationScoped
@Singleton
public class DatasourcePasswordValidator {

    static final Set<String> LOCAL_PROFILES = Set.of("dev", "leve", "test");

    private final List<String> profiles;
    private final Optional<String> password;

    @Inject
    DatasourcePasswordValidator(Config config) {
        this(config.unwrap(SmallRyeConfig.class).getProfiles(),
                config.getOptionalValue("quarkus.datasource.password", String.class));
    }

    DatasourcePasswordValidator(List<String> profiles, Optional<String> password) {
        this.profiles = List.copyOf(profiles);
        this.password = password;
    }

    void onStart(@Observes StartupEvent event) {
        validate();
    }

    void validate() {
        if (profiles.stream().anyMatch(LOCAL_PROFILES::contains)) {
            return;
        }
        String value = password.orElse("");
        if (value.isBlank()) {
            throw new IllegalStateException("The database password (POSTGRES_DB_PASSWORD) is empty for the profiles "
                    + profiles);
        }
        if (isWeak(value)) {
            throw new IllegalStateException("The database password (POSTGRES_DB_PASSWORD) is too weak for the profiles "
                    + profiles + ": use at least 12 characters, not only letters or only digits, and without "
                    + "'password' or 'admin'");
        }
    }

    static boolean isWeak(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        return password.length() < 12
                || lower.contains("password")
                || lower.contains("admin")
                || password.matches("[a-zA-Z]+")
                || password.matches("\\d+");
    }
}
