package com.estoque.web;

import java.math.BigDecimal;
import java.util.List;

/** Indicadores exibidos no dashboard inicial. */
public record ResumoDashboard(
        int totalProdutos,
        long unidadesEmEstoque,
        BigDecimal valorEmEstoque,
        List<ProdutoView> alertas,
        List<MovimentacaoView> ultimasMovimentacoes) {

    /** Quantidade maxima de alertas listados no painel do dashboard. */
    public static final int ALERTAS_EM_DESTAQUE = 8;

    public ResumoDashboard {
        alertas = List.copyOf(alertas);
        ultimasMovimentacoes = List.copyOf(ultimasMovimentacoes);
    }

    static ResumoDashboard de(List<ProdutoView> produtos, List<MovimentacaoView> ultimasMovimentacoes) {
        long unidades = 0;
        BigDecimal valor = BigDecimal.ZERO;
        for (ProdutoView produto : produtos) {
            unidades += produto.quantidade();
            valor = valor.add(produto.valorEmEstoque());
        }
        List<ProdutoView> alertas = produtos.stream()
                .filter(ProdutoView::emAlerta)
                .sorted(ProdutoView.POR_GRAVIDADE)
                .toList();
        return new ResumoDashboard(produtos.size(), unidades, valor, alertas, ultimasMovimentacoes);
    }

    public int totalAlertas() {
        return alertas.size();
    }

    public long produtosSemEstoque() {
        return alertas.stream().filter(p -> p.situacao() == SituacaoEstoque.ESGOTADO).count();
    }

    public List<ProdutoView> alertasEmDestaque() {
        return alertas.subList(0, Math.min(ALERTAS_EM_DESTAQUE, alertas.size()));
    }
}
