package br.com.vortex.application.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Ordem estável entre páginas. Descrição e data não são únicas, e o banco não garante a ordem de linhas empatadas
 * com LIMIT/OFFSET: sem desempate, um item empatado na fronteira pode aparecer em duas páginas e outro em nenhuma.
 */
final class Paginacao {

    private static final String ID = "id";

    private Paginacao() {
    }

    /** A ordenação pedida, com {@code id} ascendente por último quando ela ainda não ordena por {@code id}. */
    static Pageable comDesempatePorId(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().getOrderFor(ID) != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by(ID)));
    }
}
