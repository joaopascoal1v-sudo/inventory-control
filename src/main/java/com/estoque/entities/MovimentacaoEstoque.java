package com.estoque.entities;

import java.time.LocalDateTime;

public record MovimentacaoEstoque(
        long id,
        String codigoProduto,
        TipoMovimentacao tipo,
        DirecaoMovimentacao direcao,
        int quantidade,
        int saldoAntes,
        int saldoDepois,
        Long usuarioId,
        MotivoMovimentacao motivo,
        LocalDateTime data,
        Long estornadaPorId,
        Long estornoDeId,
        String codigoDestino) {

    public MovimentacaoEstoque {
        if (id < 0 || quantidade <= 0 || saldoAntes < 0 || saldoDepois < 0) {
            throw new IllegalArgumentException("Dados invalidos para a movimentacao.");
        }
        if (codigoProduto == null || codigoProduto.isBlank() || tipo == null || direcao == null
                || motivo == null || data == null) {
            throw new IllegalArgumentException("Dados obrigatorios da movimentacao ausentes.");
        }
    }
}