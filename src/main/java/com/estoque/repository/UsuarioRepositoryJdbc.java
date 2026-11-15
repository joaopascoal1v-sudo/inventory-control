package com.estoque.repository;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UsuarioRepositoryJdbc implements UsuarioRepository {
    private static final String EXISTS = "SELECT 1 FROM usuarios LIMIT 1";
    private static final String INSERT =
            "INSERT INTO usuarios (nome, email, senha_hash, perfil) VALUES (?, ?, ?, ?)";
    private static final String SELECT_BY_EMAIL =
            "SELECT id, nome, email, senha_hash, perfil FROM usuarios WHERE email = ?";
    private static final String SELECT_BY_ID =
            "SELECT id, nome, email, senha_hash, perfil FROM usuarios WHERE id = ?";

    @Override
    public boolean existeAlgum() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS);
             ResultSet resultado = statement.executeQuery()) {
            return resultado.next();
        }
    }

    @Override
    public void inserir(Usuario usuario, String senhaHash) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, usuario.getNome());
            statement.setString(2, usuario.getEmail());
            statement.setString(3, senhaHash);
            statement.setString(4, usuario.getPerfil().name());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_EMAIL)) {
            statement.setString(1, email);
            try (ResultSet resultado = statement.executeQuery()) {
                return resultado.next() ? Optional.of(mapearUsuario(resultado)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet resultado = statement.executeQuery()) {
                return resultado.next() ? Optional.of(mapearUsuario(resultado)) : Optional.empty();
            }
        }
    }

    private Usuario mapearUsuario(ResultSet resultado) throws SQLException {
        return new Usuario(
                resultado.getLong("id"),
                resultado.getString("nome"),
                resultado.getString("email"),
                resultado.getString("senha_hash"),
                PerfilAcesso.valueOf(resultado.getString("perfil")));
    }
}
