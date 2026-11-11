package com.estoque.entities;

import java.math.BigDecimal;

public class Produto {
    private final String codigo;
    private String nome;
    private BigDecimal preco;
    private int quantidade;

    public Produto(String codigo, String nome, BigDecimal preco, int quantidade) {
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

        this.codigo = codigo.trim();
        this.nome = nome.trim();
        this.preco = preco;
        this.quantidade = quantidade;
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
