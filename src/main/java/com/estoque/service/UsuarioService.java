package com.estoque.service;

import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.Usuario;
import com.estoque.repository.UsuarioRepository;
import com.estoque.security.SenhaHasher;

import java.sql.SQLException;
import java.util.Locale;

public class UsuarioService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        if (usuarioRepository == null) {
            throw new IllegalArgumentException("O repositorio de usuarios e obrigatorio.");
        }
        this.usuarioRepository = usuarioRepository;
    }

    public boolean temUsuarios() throws SQLException {
        return usuarioRepository.existeAlgum();
    }

    public Usuario cadastrarPrimeiroAdministrador(String nome, String email, String senha) throws SQLException {
        if (usuarioRepository.existeAlgum()) {
            throw new IllegalArgumentException("O administrador inicial ja foi cadastrado.");
        }
        return cadastrar(nome, email, senha, PerfilAcesso.ADMIN);
    }

    /**
     * Realiza o auto-cadastro na tela de login/primeiro acesso: se ainda nao houver usuarios
     * no sistema, cria como ADMIN; caso contrario, cria como FUNCIONARIO.
     */
    public Usuario registrarAutoCadastro(String nome, String email, String senha) throws SQLException {
        PerfilAcesso perfil = usuarioRepository.existeAlgum()
                ? PerfilAcesso.FUNCIONARIO
                : PerfilAcesso.ADMIN;
        return cadastrar(nome, email, senha, perfil);
    }

    public Usuario cadastrarFuncionario(
            Usuario usuarioAutenticado, String nome, String email, String senha) throws SQLException {
        exigirAdministrador(usuarioAutenticado);
        return cadastrar(nome, email, senha, PerfilAcesso.FUNCIONARIO);
    }

    public Usuario login(String email, String senha) throws SQLException {
        if (email == null || email.trim().isEmpty() || senha == null) {
            throw new IllegalArgumentException("E-mail ou senha invalidos.");
        }
        Usuario usuario = usuarioRepository.buscarPorEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha invalidos."));
        if (!usuario.senhaCorresponde(senha)) {
            throw new IllegalArgumentException("E-mail ou senha invalidos.");
        }
        return usuario;
    }

    public void exigirAdministrador(Usuario usuarioAutenticado) throws SQLException {
        if (usuarioAutenticado == null) {
            throw new IllegalArgumentException("E necessario estar autenticado.");
        }
        Usuario usuarioAtual = usuarioRepository.buscarPorId(usuarioAutenticado.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado nao encontrado."));
        if (usuarioAtual.getPerfil() != PerfilAcesso.ADMIN) {
            throw new IllegalArgumentException("Acesso permitido somente para administradores.");
        }
    }

    private Usuario cadastrar(String nome, String email, String senha, PerfilAcesso perfil) throws SQLException {
        String senhaHash = SenhaHasher.gerar(senha);
        Usuario usuario = new Usuario(0, nome, email, senhaHash, perfil);
        if (usuarioRepository.buscarPorEmail(usuario.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ja existe um usuario cadastrado com esse e-mail.");
        }
        usuarioRepository.inserir(usuario, senhaHash);
        return usuarioRepository.buscarPorEmail(usuario.getEmail())
                .orElseThrow(() -> new SQLException("O usuario foi cadastrado, mas nao foi encontrado."));
    }
}
