package com.estoque.app;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.sql.SQLException;

import javax.swing.SwingUtilities;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.Fornecedor;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.Produto;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.entities.Usuario;
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
        DashboardService dashboardService = new DashboardService(estoque, fornecedores);

        try {
            dashboardService.popularEstoqueFarmaciaSeVazio(null);
        } catch (Exception e) {
            System.err.println("Aviso ao preparar dados iniciais: " + e.getMessage());
        }

        try {
            int portaApi = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "8080"));
            api = new MovimentacaoApiServer(estoque, usuarios, portaApi);
            api.iniciar();
            System.out.println("API de estoque disponivel em http://127.0.0.1:" + api.getPorta() + "/api");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Nao foi possivel iniciar a API de estoque: " + e.getMessage());
        }

        int portaWebAtiva = 8081;
        String portaEnv = System.getenv("PORT");
        if (portaEnv == null || portaEnv.isBlank()) {
            portaEnv = System.getenv().getOrDefault("WEB_PORT", "8081");
        }
        int portaInicial = Integer.parseInt(portaEnv.trim());
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            int portaTentativa = portaInicial + tentativa;
            try {
                painelWeb = new DashboardWebServer(dashboardService, usuarios, portaTentativa);
                painelWeb.iniciar();
                portaWebAtiva = painelWeb.getPorta();
                System.out.println("==================================================================");
                System.out.println("Painel Web Online ativo:");
                System.out.println(" -> Neste computador: http://localhost:" + portaWebAtiva + "/login");
                try {
                    String ipRede = InetAddress.getLocalHost().getHostAddress();
                    System.out.println(" -> Na rede / Wi-Fi:  http://" + ipRede + ":" + portaWebAtiva + "/login");
                } catch (Exception ignored) {
                }
                System.out.println("==================================================================");
                break;
            } catch (IOException | IllegalArgumentException e) {
                if (tentativa == 9) {
                    System.err.println("Nao foi possivel iniciar o painel web: " + e.getMessage());
                }
            }
        }

        boolean webOnly = Boolean.parseBoolean(System.getenv().getOrDefault("WEB_ONLY", "false"));
        if (GraphicsEnvironment.isHeadless() || webOnly) {
            System.out.println("Modo servidor online ativo (API + Painel Web).");
            manterProcessoAtivo();
            return;
        }

        final int portaFinal = portaWebAtiva;
        abrirJanelaWebUnificada("http://localhost:" + portaFinal + "/login");
        manterProcessoAtivo();
        SwingUtilities.invokeLater(() -> new UI(estoque, fornecedores, usuarios).iniciar());
    }

    private static void abrirJanelaWebUnificada(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static void manterProcessoAtivo() {
        Thread keepAlive = new Thread(() -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "server-keep-alive");
        keepAlive.setDaemon(false);
        keepAlive.start();
    }

    private static void popularDadosExemploSeVazio(
            EstoqueService estoque,
            FornecedorService fornecedores,
            UsuarioService usuarios
    ) throws SQLException {
        if (!estoque.listar().isEmpty()) {
            return;
        }

        Usuario admin = usuarios.login("admin@estoque.com", "12345678");
        Long adminId = admin != null ? admin.getId() : null;

        // Analgesicos e Antitermicos
        estoque.cadastrar(new Produto("MED001", "Dipirona Monoidratada 500mg (10 comp.)", new BigDecimal("8.90"), 120, 25));
        estoque.cadastrar(new Produto("MED002", "Dipirona Gotas 500mg/ml Frasco 20ml", new BigDecimal("11.50"), 65, 15));
        estoque.cadastrar(new Produto("MED003", "Paracetamol 750mg Generico (20 comp.)", new BigDecimal("14.50"), 90, 20));
        estoque.cadastrar(new Produto("MED004", "Dorflex Relaxante Muscular (36 comp.)", new BigDecimal("24.90"), 80, 20));

        // Anti-inflamatorios e Antibioticos
        estoque.cadastrar(new Produto("MED005", "Ibuprofeno 600mg (20 comp.)", new BigDecimal("22.90"), 45, 12));
        estoque.cadastrar(new Produto("MED006", "Nimesulida 100mg (12 comp.)", new BigDecimal("15.90"), 55, 15));
        estoque.cadastrar(new Produto("MED007", "Amoxicilina 500mg Capsulas (21 caps.)", new BigDecimal("34.90"), 4, 12));
        estoque.cadastrar(new Produto("MED008", "Azitromicina 500mg (5 comp.)", new BigDecimal("29.90"), 6, 10));

        // Antialergicos e Gripe
        estoque.cadastrar(new Produto("MED009", "Loratadina 10mg Antialergico (12 comp.)", new BigDecimal("16.80"), 5, 15));
        estoque.cadastrar(new Produto("MED010", "Allegra 120mg (10 comp.)", new BigDecimal("42.50"), 28, 8));
        estoque.cadastrar(new Produto("MED011", "Cimegripe Capsulas (20 caps.)", new BigDecimal("18.90"), 70, 20));
        estoque.cadastrar(new Produto("MED012", "Xarope Vick Mel Expectorante 120ml", new BigDecimal("36.90"), 22, 8));

        // Estomago e Uso Continuo
        estoque.cadastrar(new Produto("MED013", "Omeprazol 20mg Protetor Gastrico (28 caps.)", new BigDecimal("19.90"), 60, 15));
        estoque.cadastrar(new Produto("MED014", "Sal de Fruta Eno Tradicional (2 env.)", new BigDecimal("5.50"), 95, 30));
        estoque.cadastrar(new Produto("MED015", "Losartana Potassica 50mg (30 comp.)", new BigDecimal("12.90"), 110, 25));
        estoque.cadastrar(new Produto("MED016", "Simeticona 125mg Antigases (10 caps.)", new BigDecimal("17.50"), 38, 10));

        // Vitaminas, Higiene e Primeiros Socorros
        estoque.cadastrar(new Produto("VIT017", "Vitamina C Redoxon 1g Efervescente (10 comp.)", new BigDecimal("26.50"), 40, 10));
        estoque.cadastrar(new Produto("VIT018", "Vitamina D3 2000UI (30 caps.)", new BigDecimal("39.90"), 34, 8));
        estoque.cadastrar(new Produto("CUR019", "Soro Fisiologico 0,9% 500ml", new BigDecimal("9.50"), 0, 15));
        estoque.cadastrar(new Produto("CUR020", "Alcool 70% Antisseptico 1 Litro", new BigDecimal("13.90"), 3, 12));
        estoque.cadastrar(new Produto("CUR021", "Curativo Band-Aid Transparente (40 un.)", new BigDecimal("19.50"), 48, 10));
        estoque.cadastrar(new Produto("DER022", "Protetor Solar Facial FPS 60 50g", new BigDecimal("69.90"), 25, 6));
        estoque.cadastrar(new Produto("INF023", "Fralda Pampers Confort Sec G (38 un.)", new BigDecimal("64.90"), 30, 8));
        estoque.cadastrar(new Produto("EQU024", "Termometro Digital Clinico G-Tech", new BigDecimal("28.00"), 16, 5));

        // Movimentacoes de exemplo (vendas e reposicoes)
        estoque.registrarMovimentacao("MED001", TipoMovimentacao.SAIDA, null, 35, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("MED004", TipoMovimentacao.SAIDA, null, 28, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("MED003", TipoMovimentacao.SAIDA, null, 22, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("MED015", TipoMovimentacao.SAIDA, null, 19, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("MED011", TipoMovimentacao.SAIDA, null, 15, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("MED013", TipoMovimentacao.SAIDA, null, 12, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("VIT017", TipoMovimentacao.SAIDA, null, 9, MotivoMovimentacao.VENDA, adminId, null);
        estoque.registrarMovimentacao("DER022", TipoMovimentacao.ENTRADA, null, 10, MotivoMovimentacao.COMPRA, adminId, null);

        if (fornecedores.listar().isEmpty()) {
            fornecedores.cadastrar(new Fornecedor(
                    "EMS S/A Industria Farmaceutica",
                    "57.507.378/0003-65",
                    "(19) 3887-9800",
                    "pedidos@ems.com.br",
                    "Rod. Jornalista F. A. Proenca, Km 08 - Hortolandia/SP"));
            fornecedores.cadastrar(new Fornecedor(
                    "Medley Farmaceutica Ltda",
                    "10.588.595/0007-97",
                    "(19) 2117-8000",
                    "comercial@medley.com.br",
                    "Rua Macedo Costa, 55 - Campinas/SP"));
            fornecedores.cadastrar(new Fornecedor(
                    "Eurofarma Laboratorios S.A.",
                    "61.190.096/0001-92",
                    "(11) 5090-8600",
                    "vendas@eurofarma.com.br",
                    "Av. Ver. Jose Diniz, 3465 - Sao Paulo/SP"));
            fornecedores.cadastrar(new Fornecedor(
                    "Neo Quimica / Hypera Pharma",
                    "02.932.074/0001-91",
                    "(11) 3627-4000",
                    "sac@hypera.com.br",
                    "Vila Industrial - Anapolis/GO"));
            fornecedores.cadastrar(new Fornecedor(
                    "Cimed Industria de Medicamentos",
                    "02.814.497/0001-07",
                    "(11) 3544-7200",
                    "pedidos@cimedremedios.com.br",
                    "Pouso Alegre/MG"));
            fornecedores.cadastrar(new Fornecedor(
                    "Panarello Distribuidora Farmaceutica",
                    "01.206.820/0001-04",
                    "(62) 3235-5000",
                    "atendimento@panarello.com.br",
                    "Av. Brasilia, 1100 - Goiania/GO"));
        }
    }
}
