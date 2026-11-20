package br.com.vortex.authorization.event;

import java.time.OffsetDateTime;

/**
 * Sessão aberta pelo login: como entrou, quando e o id da sessão. Serve só para montar o evento: no JSON publicado
 * os três continuam como os campos planos {@code loginMethod}, {@code lastLogin} e {@code sessionId} do
 * {@link UserLoggedInEvent}.
 */
public record LoginSession(String method, OffsetDateTime at, String sessionId) {
}
