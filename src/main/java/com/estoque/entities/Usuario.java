package com.estoque.entities;

import com.estoque.security.SenhaHasher;

import java.util.Locale;

public class Usuario {
    private final long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final PerfilAcesso perfil;

    public Usuario(long id, String nome, String email, String senhaHash, PerfilAcesso perfil) {
        if (id < 0) {
            throw new IllegalArgumentException("O identificador do usuario nao pode ser negativo.");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do usuario e obrigatorio.");
        }
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Informe um e-mail valido.");
        }
        if (senhaHash == null || senhaHash.trim().isEmpty()) {
            throw new IllegalArgumentException("O hash da senha e obrigatorio.");
        }
        if (perfil == null) {
            throw new IllegalArgumentException("O perfil de acesso e obrigatorio.");
        }

        this.id = id;
        this.nome = nome.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.senhaHash = senhaHash;
        this.perfil = perfil;
    }

    public long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public PerfilAcesso getPerfil() {
        return perfil;
    }

    public boolean senhaCorresponde(String senha) {
        return SenhaHasher.verificar(senha, senhaHash);
    }
}
