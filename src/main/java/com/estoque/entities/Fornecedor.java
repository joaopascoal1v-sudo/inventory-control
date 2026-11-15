package com.estoque.entities;

public class Fornecedor {
    private final long id;
    private final String nome;
    private final String cnpj;
    private final String telefone;
    private final String email;
    private final String endereco;

    public Fornecedor(String nome, String cnpj, String telefone, String email, String endereco) {
        this(0, nome, cnpj, telefone, email, endereco);
    }

    public Fornecedor(long id, String nome, String cnpj, String telefone, String email, String endereco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do fornecedor e obrigatorio.");
        }
        if (id < 0) {
            throw new IllegalArgumentException("O identificador do fornecedor nao pode ser negativo.");
        }

        this.id = id;
        this.nome = nome.trim();
        this.cnpj = normalizarOpcional(cnpj);
        this.telefone = normalizarOpcional(telefone);
        this.email = normalizarOpcional(email);
        this.endereco = normalizarOpcional(endereco);
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEmail() {
        return email;
    }

    public String getEndereco() {
        return endereco;
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        return valor.trim();
    }
}
