package com.estoque.repository;

import com.estoque.entities.Produto;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository {
    void inserir(Produto produto) throws SQLException;

    List<Produto> listar() throws SQLException;

    Optional<Produto> buscarPorCodigo(String codigo) throws SQLException;

    boolean atualizar(Produto produto) throws SQLException;

    boolean remover(String codigo) throws SQLException;
}
