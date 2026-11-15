package com.estoque.app;

import com.estoque.database.DatabaseConnection;
import com.estoque.repository.FornecedorRepositoryJdbc;
import com.estoque.repository.ProdutoRepositoryJdbc;
import com.estoque.repository.UsuarioRepositoryJdbc;
import com.estoque.service.EstoqueService;
import com.estoque.service.FornecedorService;
import com.estoque.service.UsuarioService;

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
        FornecedorService fornecedores = new FornecedorService(new FornecedorRepositoryJdbc());
        UsuarioService usuarios = new UsuarioService(new UsuarioRepositoryJdbc());
        SwingUtilities.invokeLater(() -> new UI(estoque, fornecedores, usuarios).iniciar());
    }
}
