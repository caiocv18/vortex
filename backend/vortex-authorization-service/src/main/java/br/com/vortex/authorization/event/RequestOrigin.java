package br.com.vortex.authorization.event;

/**
 * Origem da requisição que gerou o evento. Serve só para montar o evento: no JSON publicado o endereço e o agente
 * continuam como os campos planos {@code ipAddress} e {@code userAgent} do {@link AuthEvent}.
 */
public record RequestOrigin(String ipAddress, String userAgent) {
}
