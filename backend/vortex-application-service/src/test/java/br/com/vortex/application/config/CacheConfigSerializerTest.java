package br.com.vortex.application.config;

import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.dto.TipoProdutoDTO;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import br.com.vortex.application.service.TipoProdutoService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ida e volta, sem Redis, dos valores que os {@code @Cacheable} da aplicação gravam, com o serializer
 * Jackson 3 do {@link CacheConfig}, e recusa de tipos fora da lista do validador polimórfico.
 */
class CacheConfigSerializerTest {

    private final RedisSerializer<Object> serializer = CacheConfig.jsonRedisSerializer();

    @Test
    void roundTripsTipoProduto() {
        TipoProdutoDTO tipo = new TipoProdutoDTO(7L, "Eletrônicos");

        assertEquals(tipo, serializer.deserialize(serializer.serialize(tipo)));
    }

    @Test
    void roundTripsTheListCachedByTipoProdutoServiceBuscarTodos() {
        // O valor real do @Cacheable("produto-types"), não uma lista montada no teste
        TipoProdutoRepository repository = mock(TipoProdutoRepository.class);
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
                new TipoProduto(3L, "Alimentos"), new TipoProduto(5L, "Livros")));
        List<TipoProdutoDTO> tipos = new TipoProdutoService(repository, mock(ProdutoRepository.class)).buscarTodos();

        assertEquals(tipos, serializer.deserialize(serializer.serialize(tipos)));
    }

    @Test
    void roundTripsMutableList() {
        List<TipoProdutoDTO> tipos = new ArrayList<>(List.of(new TipoProdutoDTO(3L, "Roupas")));

        assertEquals(tipos, serializer.deserialize(serializer.serialize(tipos)));
    }

    @Test
    void roundTripsProdutoWithBigDecimal() {
        ProdutoDTO produto = new ProdutoDTO(10L, "Notebook", new BigDecimal("3000.00"), 5, 1L);

        assertEquals(produto, serializer.deserialize(serializer.serialize(produto)));
    }

    @Test
    void roundTripsBoolean() {
        assertEquals(Boolean.TRUE, serializer.deserialize(serializer.serialize(Boolean.TRUE)));
    }

    @Test
    void writesTypeInformationAsClassProperty() {
        String json = new String(serializer.serialize(new TipoProdutoDTO(7L, "Eletrônicos")), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"@class\":\"br.com.vortex.application.dto.TipoProdutoDTO\""), json);
    }

    @Test
    void rejectsTypeOutsideTheAllowList() {
        // Classe instanciável qualquer, fora dos pacotes permitidos: o LaissezFaire antigo a criaria
        byte[] json = ("{\"@class\":\"" + NotAllowed.class.getName() + "\",\"value\":\"x\"}")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(SerializationException.class, () -> serializer.deserialize(json));
    }

    public static class NotAllowed {
        public String value;
    }
}
