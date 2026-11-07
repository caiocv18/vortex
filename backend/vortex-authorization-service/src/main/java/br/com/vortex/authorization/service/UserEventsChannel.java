package br.com.vortex.authorization.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.concurrent.CompletionStage;

/**
 * Saída do canal user-events (conector nos perfis kafka e rabbitmq: mp.messaging.outgoing.user-events.*).
 *
 * Fica num bean próprio e @ApplicationScoped de propósito: o CDI só cria a instância, e só então liga
 * o Emitter ao canal, na primeira chamada. Sem conector no canal (padrão, test, leve) essa ligação falha
 * com SRMSG00019; como o EventPublisher só chama este bean com auth.event.enabled=true, com os eventos
 * desligados ele nunca é criado e a aplicação roda sem conector nenhum.
 */
@ApplicationScoped
public class UserEventsChannel {

    @Inject
    @Channel("user-events")
    Emitter<String> emitter;

    public CompletionStage<Void> send(String payload) {
        return emitter.send(payload);
    }
}
