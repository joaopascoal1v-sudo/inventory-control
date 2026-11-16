package com.estoque.app;

import java.awt.GraphicsEnvironment;
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
import com.estoque.web.DashboardService;
import com.estoque.web.DashboardWebServer;

public class Main {
    private static MovimentacaoApiServer api;
    private static DashboardWebServer painelWeb;

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
        try {
            int portaWeb = Integer.parseInt(System.getenv().getOrDefault("WEB_PORT", "8081"));
            painelWeb = new DashboardWebServer(new DashboardService(estoque), usuarios, portaWeb);
            painelWeb.iniciar();
            System.out.println("Painel web disponivel em http://127.0.0.1:" + painelWeb.getPorta() + "/");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Nao foi possivel iniciar o painel web: " + e.getMessage());
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Ambiente sem interface grafica: somente a API e o painel web estao ativos.");
            return;
        }
        SwingUtilities.invokeLater(() -> new UI(estoque, fornecedores, usuarios).iniciar());
    }
}
