package br.com.vortex.authorization.service;

import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.concurrent.CompletionStage;

/**
 * Saída do canal user-events (conector nos perfis kafka e rabbitmq: mp.messaging.outgoing.user-events.*).
 *
 * Fica num bean próprio e @ApplicationScoped de propósito. O Emitter entra no grafo de canais já na
 * subida, mas sem conector no canal (padrão, test, leve) a subida só avisa que ele está sem consumidor;
 * criar este bean é que falharia (SRMSG00019). Como o EventPublisher só chama este bean com
 * auth.event.enabled=true, com os eventos desligados ele nunca é criado e a aplicação roda sem conector.
 *
 * @Broadcast: no build dev o canal tem dois consumidores, o AuthEventConsumer de exemplo e, com
 * -m kafka ou -m rabbitmq, o conector de saída. Um Emitter sem @Broadcast aceita um consumidor só, e o
 * quarkus:dev com mensageria não subia (TooManyDownstreamCandidatesException). Fora do dev há no máximo
 * um consumidor e nada muda.
 */
@ApplicationScoped
public class UserEventsChannel {

    @Inject
    @Channel("user-events")
    @Broadcast
    Emitter<String> emitter;

    public CompletionStage<Void> send(String payload) {
        return emitter.send(payload);
    }
}
