package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Tira o loopback da lista de proxies confiáveis. O cliente de teste conecta por loopback, então
 * aqui ele faz o papel de um cliente qualquer da internet: o X-Forwarded-For que mandar deve ser
 * ignorado. 198.18.0.1 (faixa de benchmark, RFC 2544) só ocupa a lista, nenhuma conexão vem dele.
 */
public class UntrustedProxyTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.http.proxy.trusted-proxies", "198.18.0.1");
    }
}
