package com.estoque.entities;

import java.math.BigDecimal;

public class Produto {
    private final String codigo;
    private String nome;
    private BigDecimal preco;
    private int quantidade;
    private int estoqueMinimo;

    public Produto(String codigo, String nome, BigDecimal preco, int quantidade) {
        this(codigo, nome, preco, quantidade, 0);
    }

    public Produto(String codigo, String nome, BigDecimal preco, int quantidade, int estoqueMinimo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new IllegalArgumentException("O codigo do produto e obrigatorio.");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do produto e obrigatorio.");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("O preco nao pode ser negativo.");
        }
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade nao pode ser negativa.");
        }
        if (estoqueMinimo < 0) {
            throw new IllegalArgumentException("O estoque minimo nao pode ser negativo.");
        }

        this.codigo = codigo.trim();
        this.nome = nome.trim();
        this.preco = preco;
        this.quantidade = quantidade;
        this.estoqueMinimo = estoqueMinimo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public int getEstoqueMinimo() {
        return estoqueMinimo;
    }

    /**
     * Indica se o produto deve gerar alerta de estoque minimo. Produtos com
     * estoque minimo zero nao possuem limite configurado e nunca geram alerta.
     */
    public boolean estaAbaixoDoMinimo() {
        return estoqueMinimo > 0 && quantidade < estoqueMinimo;
    }

    public void atualizar(String nome, BigDecimal preco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do produto e obrigatorio.");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("O preco nao pode ser negativo.");
        }
        this.nome = nome.trim();
        this.preco = preco;
    }

    public void atualizarEstoqueMinimo(int estoqueMinimo) {
        if (estoqueMinimo < 0) {
            throw new IllegalArgumentException("O estoque minimo nao pode ser negativo.");
        }
        this.estoqueMinimo = estoqueMinimo;
    }

    public void adicionarEstoque(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        this.quantidade = Math.addExact(this.quantidade, quantidade);
    }

    public void retirarEstoque(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        if (quantidade > this.quantidade) {
            throw new IllegalArgumentException("Estoque insuficiente.");
        }
        this.quantidade -= quantidade;
    }
}
