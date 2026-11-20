package br.com.vortex.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PaginacaoTest {

    @Test
    void appendsTheIdAfterTheRequestedOrder() {
        Pageable pedido = PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "dataMovimento"));

        Pageable estavel = Paginacao.comDesempatePorId(pedido);

        assertEquals(2, estavel.getPageNumber());
        assertEquals(20, estavel.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "dataMovimento").and(Sort.by("id")), estavel.getSort());
    }

    @Test
    void anUnsortedRequestIsOrderedById() {
        assertEquals(Sort.by("id"), Paginacao.comDesempatePorId(PageRequest.of(0, 5)).getSort());
    }

    @Test
    void keepsARequestThatAlreadyOrdersById() {
        Pageable pedido = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id"));

        assertSame(pedido, Paginacao.comDesempatePorId(pedido));
    }

    @Test
    void keepsAnUnpagedRequest() {
        assertSame(Pageable.unpaged(), Paginacao.comDesempatePorId(Pageable.unpaged()));
    }
}
