package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Operador que pôs um proxy reverso na frente do serviço e o declarou em TRUSTED_PROXIES. O cliente de
 * teste conecta por loopback, então aqui ele faz o papel desse proxy: o X-Forwarded-For que mandar vale
 * como IP do cliente. Sem TRUSTED_PROXIES (o padrão) o cabeçalho é ignorado: ver o UntrustedProxyLoginTest.
 *
 * A chave sobrescrita é a própria TRUSTED_PROXIES, não quarkus.http.proxy.trusted-proxies: assim o teste
 * passa pela mesma expressão do application.properties que o operador usa.
 */
public class TrustedLoopbackProxyTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("TRUSTED_PROXIES", "127.0.0.1,[::1]");
    }
}
