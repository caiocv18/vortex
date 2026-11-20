package br.com.vortex.application.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mudança de preço nos dois consumidores de eventos de produto: compara o valor, não a escala, e um preço
 * anterior ausente conta como mudança (como antes, quando equals(null) dava false).
 */
class ProdutoPriceChangeTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "10.0, 10.00, false",
        "10.00, 10.00, false",
        "10.00, 9.99, true",
        "10.00, null, true"
    })
    void comparesTheValueNotTheScale(BigDecimal atual, BigDecimal anterior, boolean mudou) {
        assertEquals(mudou, KafkaConsumerService.mudouValor(atual, anterior));
        assertEquals(mudou, RabbitMQConsumerService.mudouValor(atual, anterior));
    }
}
