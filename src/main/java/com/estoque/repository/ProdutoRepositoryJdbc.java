package com.estoque.repository;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProdutoRepositoryJdbc implements ProdutoRepository {
    private static final String INSERT =
            "INSERT INTO produtos (codigo, nome, preco, quantidade) VALUES (?, ?, ?, ?)";
    private static final String SELECT_ALL =
            "SELECT codigo, nome, preco, quantidade FROM produtos ORDER BY codigo";
    private static final String SELECT_BY_CODE =
            "SELECT codigo, nome, preco, quantidade FROM produtos WHERE codigo = ?";
    private static final String UPDATE =
            "UPDATE produtos SET nome = ?, preco = ?, quantidade = ? WHERE codigo = ?";
    private static final String DELETE = "DELETE FROM produtos WHERE codigo = ?";

    @Override
    public void inserir(Produto produto) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, produto.getCodigo());
            statement.setString(2, produto.getNome());
            statement.setBigDecimal(3, produto.getPreco());
            statement.setInt(4, produto.getQuantidade());
            statement.executeUpdate();
        }
    }

    @Override
    public List<Produto> listar() throws SQLException {
        List<Produto> produtos = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultado = statement.executeQuery()) {
            while (resultado.next()) {
                produtos.add(mapearProduto(resultado));
            }
        }
        return produtos;
    }

    @Override
    public Optional<Produto> buscarPorCodigo(String codigo) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CODE)) {
            statement.setString(1, codigo);
            try (ResultSet resultado = statement.executeQuery()) {
                if (resultado.next()) {
                    return Optional.of(mapearProduto(resultado));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean atualizar(Produto produto) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, produto.getNome());
            statement.setBigDecimal(2, produto.getPreco());
            statement.setInt(3, produto.getQuantidade());
            statement.setString(4, produto.getCodigo());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean remover(String codigo) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, codigo);
            return statement.executeUpdate() > 0;
        }
    }

    private Produto mapearProduto(ResultSet resultado) throws SQLException {
        return new Produto(
                resultado.getString("codigo"),
                resultado.getString("nome"),
                resultado.getBigDecimal("preco"),
                resultado.getInt("quantidade"));
    }
}
