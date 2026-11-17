package com.estoque.web;

import java.util.List;

/** Resultado da consulta de historico exibida na tela de movimentacoes. */
public record HistoricoMovimentacoes(List<MovimentacaoView> itens, int totalEncontrado) {

    public HistoricoMovimentacoes {
        itens = List.copyOf(itens);
    }

    /** Indica que existem mais registros do que os exibidos. */
    public boolean truncado() {
        return totalEncontrado > itens.size();
    }
}
