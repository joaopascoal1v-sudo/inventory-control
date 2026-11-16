package com.estoque.app;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.entities.Usuario;
import com.estoque.service.EstoqueService;
import com.estoque.service.UsuarioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class MovimentacaoApiServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EstoqueService estoque;
    private final UsuarioService usuarios;
    private final HttpServer server;

    public MovimentacaoApiServer(EstoqueService estoque, UsuarioService usuarios, int port)
            throws IOException {
        if (estoque == null || usuarios == null || port < 0 || port > 65535) {
            throw new IllegalArgumentException("Configuracao invalida para a API de estoque.");
        }
        this.estoque = estoque;
        this.usuarios = usuarios;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/", this::atender);
    }

    public void iniciar() {
        server.start();
    }

    public int getPorta() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(1);
    }

    private void atender(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
            Usuario usuario = autenticar(exchange);
            if (usuario == null) {
                responder(exchange, 401, Map.of("erro", "Autenticacao necessaria."));
                return;
            }
            String caminho = exchange.getRequestURI().getPath();
            String metodo = exchange.getRequestMethod();
            if ("/api/movimentacoes".equals(caminho) && "POST".equals(metodo)) {
                criarMovimentacao(exchange, usuario);
            } else if ("/api/movimentacoes".equals(caminho) && "GET".equals(metodo)) {
                listarMovimentacoes(exchange);
            } else if (caminho.matches("/api/movimentacoes/[0-9]+/estorno")
                    && "POST".equals(metodo)) {
                estornarMovimentacao(exchange, usuario, caminho);
            } else if ("/api/estoque/alertas-minimo".equals(caminho) && "GET".equals(metodo)) {
                responder(exchange, 200, estoque.listarAlertasEstoqueMinimo());
            } else if ("/api/estoque/relatorio".equals(caminho) && "GET".equals(metodo)) {
                responder(exchange, 200, estoque.relatorioEstoque());
            } else {
                responder(exchange, 404, Map.of("erro", "Rota nao encontrada."));
            }
            } catch (IllegalArgumentException e) {
                responder(exchange, 400, Map.of("erro", mensagem(e)));
            } catch (SQLException e) {
                responder(exchange, 500, Map.of("erro", "Erro ao acessar o banco de dados."));
            } catch (JsonProcessingException e) {
                responder(exchange, 400, Map.of("erro", "Corpo JSON invalido."));
            }
        }
    }

    private Usuario autenticar(HttpExchange exchange) throws SQLException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"estoque\"");
            return null;
        }
        String credenciais;
        try {
            credenciais = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int separador = credenciais.indexOf(':');
        if (separador < 1) {
            return null;
        }
        try {
            return usuarios.login(credenciais.substring(0, separador), credenciais.substring(separador + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void criarMovimentacao(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        CriacaoMovimentacao dados;
        try {
            dados = JSON.readValue(exchange.getRequestBody(), CriacaoMovimentacao.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Corpo JSON invalido.", e);
        }
        responder(exchange, 201, estoque.registrarMovimentacao(
                dados.codigoProduto(), dados.tipo(), dados.direcao(), dados.quantidade(),
                dados.motivo(), usuario.getId(), dados.codigoDestino()));
    }

    private void listarMovimentacoes(HttpExchange exchange) throws SQLException, IOException {
        Map<String, String> filtros = parametros(exchange.getRequestURI().getRawQuery());
        String produto = filtros.get("produto");
        TipoMovimentacao tipo = enumOpcional(filtros.get("tipo"), TipoMovimentacao.class);
        LocalDate inicio = dataOpcional(filtros.get("inicio"));
        LocalDate fim = dataOpcional(filtros.get("fim"));
        responder(exchange, 200, estoque.listarHistorico(produto, tipo, inicio, fim));
    }

    private void estornarMovimentacao(HttpExchange exchange, Usuario usuario, String caminho)
            throws SQLException, IOException {
        long id = Long.parseLong(caminho.split("/")[3]);
        responder(exchange, 201, estoque.estornarMovimentacao(id, usuario.getId()));
    }

    private void responder(HttpExchange exchange, int status, Object corpo) throws IOException {
        byte[] resposta = JSON.writeValueAsBytes(corpo);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, resposta.length);
        exchange.getResponseBody().write(resposta);
    }

    private Map<String, String> parametros(String query) {
        Map<String, String> parametros = new HashMap<>();
        if (query == null || query.isBlank()) {
            return parametros;
        }
        for (String parte : query.split("&")) {
            String[] chaveValor = parte.split("=", 2);
            parametros.put(URLDecoder.decode(chaveValor[0], StandardCharsets.UTF_8),
                    chaveValor.length == 2 ? URLDecoder.decode(chaveValor[1], StandardCharsets.UTF_8) : "");
        }
        return parametros;
    }

    private <T extends Enum<T>> T enumOpcional(String valor, Class<T> tipo) {
        return valor == null || valor.isBlank() ? null : Enum.valueOf(tipo, valor.toUpperCase(Locale.ROOT));
    }

    private LocalDate dataOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : LocalDate.parse(valor);
    }

    private String mensagem(Exception e) {
        return e.getMessage() == null ? "Requisicao invalida." : e.getMessage();
    }

    public record CriacaoMovimentacao(
            String codigoProduto,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo,
            String codigoDestino) {
    }
}