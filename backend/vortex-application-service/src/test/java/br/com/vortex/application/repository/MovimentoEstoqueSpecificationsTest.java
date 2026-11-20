package br.com.vortex.application.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O trecho pesquisado entra no LIKE como texto: {@code %}, {@code _} e o próprio caractere de escape saem
 * escapados, e o resto passa intacto.
 */
class MovimentoEstoqueSpecificationsTest {

    @Test
    void escapaOsCuringasDoLikeEOProprioEscape() {
        assertEquals("100\\% algodão", MovimentoEstoqueSpecifications.escaparCuringas("100% algodão"));
        assertEquals("fio\\_2mm", MovimentoEstoqueSpecifications.escaparCuringas("fio_2mm"));
        assertEquals("c:\\\\temp", MovimentoEstoqueSpecifications.escaparCuringas("c:\\temp"));
    }

    @Test
    void textoSemCuringaPassaIntacto() {
        assertEquals("caderno universitário", MovimentoEstoqueSpecifications.escaparCuringas("caderno universitário"));
    }
}
