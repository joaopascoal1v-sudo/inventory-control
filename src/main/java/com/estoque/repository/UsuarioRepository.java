package com.estoque.repository;

import com.estoque.entities.Usuario;

import java.sql.SQLException;
import java.util.Optional;

public interface UsuarioRepository {
    boolean existeAlgum() throws SQLException;

    void inserir(Usuario usuario, String senhaHash) throws SQLException;

    Optional<Usuario> buscarPorEmail(String email) throws SQLException;

    Optional<Usuario> buscarPorId(long id) throws SQLException;
}
