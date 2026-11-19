package br.com.vortex.application.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A chamada ao auth roda em outro thread ({@code supplyAsync}); sem propagar o contexto, o span HTTP de cliente
 * nasce sem pai e o trace da requisição se parte em dois no Tempo. Aqui a observação da requisição é aberta no
 * thread do teste e o span de cliente tem de sair como filho dela.
 */
class AuthServiceClientContextPropagationTest {

    private final ObservationRegistry registry = ObservationRegistry.create();
    private final List<Observation.Context> clientRequests = new CopyOnWriteArrayList<>();
    private ObservationRegistry previousAccessorRegistry;
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStart(Observation.Context context) {
                if ("http.client.requests".equals(context.getName())) {
                    clientRequests.add(context);
                }
            }
        });
        // O ContextSnapshot restaura a observação pelo accessor global; no app o Boot liga o registry dele ali.
        previousAccessorRegistry = ObservationThreadLocalAccessor.getInstance().getObservationRegistry();
        ObservationThreadLocalAccessor.getInstance().setObservationRegistry(registry);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/validate-token", exchange -> {
            byte[] body = "{\"valid\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ObservationThreadLocalAccessor.getInstance().setObservationRegistry(previousAccessorRegistry);
    }

    @Test
    void theAuthCallSpanIsAChildOfTheRequestObservation() {
        AuthServiceClient client = new AuthServiceClient(RestClient.builder().observationRegistry(registry),
                new AuthTokenVerifier(null, AuthTokenVerifier.DEFAULT_ISSUER),
                "http://127.0.0.1:" + server.getAddress().getPort());
        Observation request = Observation.start("http.server.requests", registry);

        try (Observation.Scope ignored = request.openScope()) {
            client.validateToken("abc.def.ghi").join();
        } finally {
            request.stop();
            client.shutdownExecutor();
        }

        assertEquals(1, clientRequests.size());
        assertSame(request, clientRequests.getFirst().getParentObservation());
    }

    @Test
    void theExecutorRestoresTheCallerObservationInAnotherThread() throws Exception {
        ExecutorService executor = AuthServiceClient.contextPropagatingExecutor();
        Observation request = Observation.start("http.server.requests", registry);
        AtomicReference<Observation> seen = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();

        try (Observation.Scope ignored = request.openScope()) {
            executor.submit(() -> {
                seen.set(registry.getCurrentObservation());
                worker.set(Thread.currentThread());
            }).get();
        } finally {
            request.stop();
            executor.shutdown();
        }

        assertNotEquals(Thread.currentThread(), worker.get());
        assertSame(request, seen.get());
    }
}
