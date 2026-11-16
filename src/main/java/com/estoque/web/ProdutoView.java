package com.estoque.web;

import java.math.BigDecimal;
import java.util.Comparator;

import com.estoque.entities.Produto;

/** Dados de um produto prontos para exibicao nas telas web. */
public record ProdutoView(
        String codigo,
        String nome,
        BigDecimal preco,
        int quantidade,
        int estoqueMinimo,
        SituacaoEstoque situacao) {

    /** Produtos mais criticos primeiro: menor cobertura do minimo, depois codigo. */
    public static final Comparator<ProdutoView> POR_GRAVIDADE =
            Comparator.comparingDouble(ProdutoView::coberturaDoMinimo)
                    .thenComparing(ProdutoView::codigo);

    public static ProdutoView de(Produto produto) {
        return new ProdutoView(
                produto.getCodigo(),
                produto.getNome(),
                produto.getPreco(),
                produto.getQuantidade(),
                produto.getEstoqueMinimo(),
                SituacaoEstoque.de(produto));
    }

    public boolean emAlerta() {
        return situacao.isAlerta();
    }

    public boolean possuiMinimo() {
        return estoqueMinimo > 0;
    }

    /** Unidades que faltam para atingir o estoque minimo. */
    public int faltam() {
        return Math.max(0, estoqueMinimo - quantidade);
    }

    public BigDecimal valorEmEstoque() {
        return preco.multiply(BigDecimal.valueOf(quantidade));
    }

    /**
     * Percentual do estoque minimo coberto pela quantidade atual, limitado a 100
     * e arredondado para baixo em passos de 5 (usado pela barra de nivel).
     */
    public int nivelPercentual() {
        if (!possuiMinimo()) {
            return 100;
        }
        long percentual = Math.min(100L, (long) quantidade * 100 / estoqueMinimo);
        return (int) (percentual - percentual % 5);
    }

    private double coberturaDoMinimo() {
        return possuiMinimo() ? (double) quantidade / estoqueMinimo : Double.MAX_VALUE;
    }
}
