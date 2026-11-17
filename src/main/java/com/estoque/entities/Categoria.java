package com.estoque.entities;

/**
 * Representa uma categoria de produtos do estoque.
 */
public class Categoria {

    private final int id;
    private String nome;
    private String descricao;

    public Categoria(int id, String nome, String descricao) {
        if (id < 0) {
            throw new IllegalArgumentException("O ID da categoria nao pode ser negativo.");
        }

        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da categoria e obrigatorio.");
        }

        this.id = id;
        this.nome = nome.trim();
        this.descricao = descricao == null ? "" : descricao.trim();
    }

    public Categoria(String nome, String descricao) {
        this(0, nome, descricao);
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void atualizar(String nome, String descricao) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da categoria e obrigatorio.");
        }

        this.nome = nome.trim();
        this.descricao = descricao == null ? "" : descricao.trim();
    }

    @Override
    public String toString() {
        return nome;
    }
}
