package com.estoque.web;

import java.time.LocalDateTime;
import java.util.Locale;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.TipoMovimentacao;

/** Movimentacao de estoque com o nome do produto e rotulos para exibicao. */
public record MovimentacaoView(
        long id,
        LocalDateTime data,
        String codigoProduto,
        String nomeProduto,
        TipoMovimentacao tipo,
        DirecaoMovimentacao direcao,
        int quantidade,
        int saldoAntes,
        int saldoDepois,
        MotivoMovimentacao motivo,
        Long estornadaPorId,
        Long estornoDeId,
        String codigoDestino) {

    public static MovimentacaoView de(MovimentacaoEstoque movimento, String nomeProduto) {
        return new MovimentacaoView(
                movimento.id(),
                movimento.data(),
                movimento.codigoProduto(),
                nomeProduto,
                movimento.tipo(),
                movimento.direcao(),
                movimento.quantidade(),
                movimento.saldoAntes(),
                movimento.saldoDepois(),
                movimento.motivo(),
                movimento.estornadaPorId(),
                movimento.estornoDeId(),
                movimento.codigoDestino());
    }

    public boolean entrada() {
        return direcao == DirecaoMovimentacao.ENTRADA;
    }

    public boolean estornada() {
        return estornadaPorId != null;
    }

    public boolean estorno() {
        return estornoDeId != null;
    }

    /** Quantidade com sinal: "+5" para entradas e "−3" (sinal de menos) para saidas. */
    public String quantidadeComSinal() {
        return (entrada() ? "+" : "−") + quantidade;
    }

    public String rotuloTipo() {
        return Rotulos.tipo(tipo);
    }

    public String rotuloMotivo() {
        return Rotulos.motivo(motivo);
    }

    /** Rotulo da operacao: o tipo, ou "Estorno de saída" para estornos. */
    public String rotuloOperacao() {
        return estorno()
                ? "Estorno de " + rotuloTipo().toLowerCase(Locale.ROOT)
                : rotuloTipo();
    }

    /** Descricao curta: operacao e motivo, sem repetir o motivo quando igual ao tipo. */
    public String descricao() {
        if (estorno() || rotuloMotivo().equals(rotuloTipo())) {
            return rotuloOperacao();
        }
        return rotuloTipo() + " · " + rotuloMotivo();
    }
}
