package com.estoque.web;

import com.estoque.entities.Produto;

/** Situacao visual do estoque de um produto em relacao ao seu estoque minimo. */
public enum SituacaoEstoque {
    ESGOTADO("Sem estoque", "esgotado"),
    BAIXO("Estoque baixo", "baixo"),
    NORMAL("Normal", "normal");

    private final String rotulo;
    private final String classe;

    SituacaoEstoque(String rotulo, String classe) {
        this.rotulo = rotulo;
        this.classe = classe;
    }

    public static SituacaoEstoque de(Produto produto) {
        if (!produto.estaAbaixoDoMinimo()) {
            return NORMAL;
        }
        return produto.getQuantidade() == 0 ? ESGOTADO : BAIXO;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Sufixo usado nas classes CSS (situacao-*, linha-*). */
    public String getClasse() {
        return classe;
    }

    public boolean isAlerta() {
        return this != NORMAL;
    }
}
