package br.com.vortex.application.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Produção e nuvem decididas pelo nome inteiro de cada perfil ativo. Com o {@code contains} de antes,
 * {@code preprod} contava como produção; {@code cloudy} não pode contar como nuvem.
 */
class DeploymentProfilesTest {

    @ParameterizedTest(name = "[{0}] produção={1}, nuvem={2}")
    @CsvSource(delimiter = ';', value = {
            "cloud,postgres,sqs; true; true",
            "cloud; true; true",
            "prd; true; false",
            "prd,kafka; true; false",
            "prod; true; false",
            "production; true; false",
            "dev,postgres; false; false",
            "dev,leve; false; false",
            "test; false; false",
            "preprod; false; false",
            "cloudy; false; false",
            "''; false; false"
    })
    void decidesByTheWholeProfileName(String profiles, boolean production, boolean cloud) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.isEmpty() ? new String[0] : profiles.split(","));

        assertEquals(production, DeploymentProfiles.isProduction(environment), "produção");
        assertEquals(cloud, DeploymentProfiles.isCloud(environment), "nuvem");
    }
}
