
package com.estoque.entities;

public class ProdutoMovimentado {

    private final String codigo;
    private final String nome;
    private final int quantidadeMovimentada;

    public ProdutoMovimentado(
            String codigo,
            String nome,
            int quantidadeMovimentada
    ) {
        this.codigo = codigo;
        this.nome = nome;
        this.quantidadeMovimentada = quantidadeMovimentada;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public int getQuantidadeMovimentada() {
        return quantidadeMovimentada;
    }
}
