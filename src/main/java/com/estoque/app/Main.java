package com.estoque.app;

import java.io.IOException;
import java.sql.SQLException;

import javax.swing.SwingUtilities;

import com.estoque.database.DatabaseConnection;
import com.estoque.repository.FornecedorRepositoryJdbc;
import com.estoque.repository.ProdutoRepositoryJdbc;
import com.estoque.repository.UsuarioRepositoryJdbc;
import com.estoque.service.EstoqueService;
import com.estoque.service.FornecedorService;
import com.estoque.service.UsuarioService;

public class Main {
    private static MovimentacaoApiServer api;

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
        try {
            int portaApi = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "8080"));
            api = new MovimentacaoApiServer(estoque, usuarios, portaApi);
            api.iniciar();
            System.out.println("API de estoque disponivel em http://127.0.0.1:" + api.getPorta() + "/api");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Nao foi possivel iniciar a API de estoque: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> new UI(estoque, fornecedores, usuarios).iniciar());
    }
}
