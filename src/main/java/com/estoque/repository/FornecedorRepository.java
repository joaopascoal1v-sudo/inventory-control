package com.estoque.repository;

import com.estoque.entities.Fornecedor;

import java.sql.SQLException;
import java.util.List;

public interface FornecedorRepository {
    void inserir(Fornecedor fornecedor) throws SQLException;

    List<Fornecedor> listar() throws SQLException;
}
