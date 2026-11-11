package com.estoque.app;

import com.estoque.database.DatabaseConnection;
import com.estoque.repository.ProdutoRepositoryJdbc;
import com.estoque.service.EstoqueService;

import java.sql.SQLException;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        try {
            DatabaseConnection.initialize();
        } catch (SQLException e) {
            System.err.println("Nao foi possivel inicializar o banco de dados: " + e.getMessage());
            return;
        }

        EstoqueService estoque = new EstoqueService(new ProdutoRepositoryJdbc());
        SwingUtilities.invokeLater(() -> new UI(estoque).iniciar());
    }
}
