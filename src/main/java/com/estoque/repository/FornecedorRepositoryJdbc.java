package com.estoque.repository;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.Fornecedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FornecedorRepositoryJdbc implements FornecedorRepository {
    private static final String INSERT =
            "INSERT INTO fornecedores (nome, cnpj, telefone, email, endereco) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_ALL =
            "SELECT id, nome, cnpj, telefone, email, endereco FROM fornecedores ORDER BY nome";

    @Override
    public void inserir(Fornecedor fornecedor) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, fornecedor.getNome());
            statement.setString(2, fornecedor.getCnpj());
            statement.setString(3, fornecedor.getTelefone());
            statement.setString(4, fornecedor.getEmail());
            statement.setString(5, fornecedor.getEndereco());
            statement.executeUpdate();
        }
    }

    @Override
    public List<Fornecedor> listar() throws SQLException {
        List<Fornecedor> fornecedores = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultado = statement.executeQuery()) {
            while (resultado.next()) {
                fornecedores.add(new Fornecedor(
                        resultado.getLong("id"),
                        resultado.getString("nome"),
                        resultado.getString("cnpj"),
                        resultado.getString("telefone"),
                        resultado.getString("email"),
                        resultado.getString("endereco")));
            }
        }
        return fornecedores;
    }
}
