package com.estoque.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.Produto;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.service.EstoqueService;
import com.estoque.service.UsuarioService;

class DashboardWebServerTest {
    private static final String EMAIL = "ana@empresa.com";
    private static final String SENHA = "senha-segura-123";

    private final HttpClient cliente = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private RelogioAjustavel relogio;
    private DashboardWebServer servidor;
    private String base;

    @BeforeEach
    void iniciar() throws IOException, SQLException {
        RepositoriosEmMemoria.Produtos produtos = new RepositoriosEmMemoria.Produtos();
        RepositoriosEmMemoria.Movimentacoes movimentacoes = new RepositoriosEmMemoria.Movimentacoes();
        produtos.inserir(new Produto("P1", "Parafuso sextavado", new BigDecimal("0.35"), 4, 50));
        produtos.inserir(new Produto("P2", "Porca M8", new BigDecimal("0.20"), 0, 30));
        produtos.inserir(new Produto("P3", "<script>alert(1)</script>", new BigDecimal("2.00"), 80, 10));
        movimentacoes.adicionar("P1", TipoMovimentacao.SAIDA, DirecaoMovimentacao.SAIDA, 46, 50,
                LocalDateTime.of(2026, 11, 16, 9, 30));
        movimentacoes.adicionar("P3", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 80, 0,
                LocalDateTime.of(2026, 11, 16, 10, 15));

        UsuarioService usuarios = new UsuarioService(new RepositoriosEmMemoria.Usuarios());
        usuarios.cadastrarPrimeiroAdministrador("Ana Souza", EMAIL, SENHA);

        relogio = new RelogioAjustavel(Instant.parse("2026-11-16T12:00:00Z"));
        servidor = new DashboardWebServer(
                new DashboardService(new EstoqueService(produtos, movimentacoes)), usuarios, 0, relogio);
        servidor.iniciar();
        base = "http://127.0.0.1:" + servidor.getPorta();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    @Test
    void paginasProtegidasRedirecionamParaLogin() throws Exception {
        HttpResponse<String> raiz = get("/", null);
        HttpResponse<String> dashboard = get("/dashboard", null);
        HttpResponse<String> login = get("/login", null);

        assertEquals(303, raiz.statusCode());
        assertEquals("/dashboard", raiz.headers().firstValue("Location").orElse(""));
        assertEquals(303, dashboard.statusCode());
        assertEquals("/login", dashboard.headers().firstValue("Location").orElse(""));
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("Entrar no painel"));
    }

    @Test
    void loginInvalidoMostraErroSemCriarSessao() throws Exception {
        HttpResponse<String> resposta = postarLogin(EMAIL, "senha-errada", null);

        assertEquals(401, resposta.statusCode());
        assertTrue(resposta.body().contains("E-mail ou senha inválidos."));
        assertTrue(resposta.body().contains("value=\"ana@empresa.com\""));
        assertTrue(resposta.headers().firstValue("Set-Cookie").isEmpty());
    }

    @Test
    void dashboardMostraIndicadoresAlertasEUltimasMovimentacoes() throws Exception {
        String cookie = entrar();

        HttpResponse<String> resposta = get("/dashboard", cookie);
        String html = resposta.body();

        assertEquals(200, resposta.statusCode());
        assertTrue(html.contains("Total de produtos"));
        assertTrue(html.contains("2 produtos estão abaixo do estoque mínimo"));
        assertTrue(html.contains("· 1 sem estoque"));
        assertTrue(html.contains("Parafuso sextavado"));
        assertTrue(html.contains("Porca M8"));
        assertTrue(html.contains("linha-esgotado"));
        assertTrue(html.contains("linha-baixo"));
        assertTrue(html.contains("Últimas movimentações"));
        assertTrue(html.contains("16/11/2026 10:15"));
        assertTrue(html.contains("−46"));
        assertTrue(html.contains("Ana Souza"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(resposta.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
        assertEquals("no-store", resposta.headers().firstValue("Cache-Control").orElse(""));
        // Com "no-referrer" o navegador enviaria "Origin: null" no login e o painel o recusaria.
        assertEquals("same-origin", resposta.headers().firstValue("Referrer-Policy").orElse(""));
    }

    @Test
    void telaDeProdutosDestacaEFiltraEstoqueBaixo() throws Exception {
        String cookie = entrar();

        String todos = get("/produtos", cookie).body();
        String baixo = get("/produtos?situacao=baixo", cookie).body();
        String busca = get("/produtos?busca=porca", cookie).body();

        assertTrue(todos.contains("Parafuso sextavado"));
        assertTrue(todos.contains("&lt;script&gt;"));
        assertTrue(todos.contains("selo-esgotado"));
        assertTrue(todos.contains("faltam 46 unidades"));
        assertTrue(baixo.contains("Parafuso sextavado"));
        assertTrue(baixo.contains("Porca M8"));
        assertFalse(baixo.contains("&lt;script&gt;"));
        assertTrue(busca.contains("Porca M8"));
        assertFalse(busca.contains("Parafuso sextavado"));
    }

    @Test
    void telaDeMovimentacoesFiltraEValidaDatas() throws Exception {
        String cookie = entrar();

        String todas = get("/movimentacoes", cookie).body();
        String entradas = get("/movimentacoes?tipo=ENTRADA", cookie).body();
        String invalida = get("/movimentacoes?inicio=2026-13-01", cookie).body();
        String invertida = get("/movimentacoes?inicio=2026-11-20&fim=2026-11-01", cookie).body();

        assertTrue(todas.contains("2 movimentações encontradas"));
        assertTrue(entradas.contains("1 movimentação encontrada"));
        assertTrue(invalida.contains("A data inicial é inválida"));
        assertTrue(invertida.contains("A data inicial não pode ser posterior à data final."));
    }

    @Test
    void logoutEncerraASessao() throws Exception {
        String cookie = entrar();

        HttpResponse<String> saida = enviar(HttpRequest.newBuilder(URI.create(base + "/logout"))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody()));
        HttpResponse<String> depois = get("/dashboard", cookie);

        assertEquals(303, saida.statusCode());
        assertEquals("/login?saiu", saida.headers().firstValue("Location").orElse(""));
        assertTrue(saida.headers().firstValue("Set-Cookie").orElse("").contains("Max-Age=0"));
        assertEquals(303, depois.statusCode());
        assertEquals("/login?expirou", depois.headers().firstValue("Location").orElse(""));
    }

    @Test
    void sessaoExpiraAposInatividade() throws Exception {
        String cookie = entrar();
        assertEquals(200, get("/dashboard", cookie).statusCode());

        relogio.avancar(Duration.ofMinutes(29));
        assertEquals(200, get("/dashboard", cookie).statusCode());

        relogio.avancar(Duration.ofMinutes(31));
        HttpResponse<String> expirada = get("/dashboard", cookie);
        assertEquals(303, expirada.statusCode());
        assertEquals("/login?expirou", expirada.headers().firstValue("Location").orElse(""));
    }

    @Test
    void recusaFormularioDeOutraOrigem() throws Exception {
        HttpResponse<String> resposta = postarLogin(EMAIL, SENHA, "http://site-malicioso.example");
        HttpResponse<String> origemNula = postarLogin(EMAIL, SENHA, "null");

        assertEquals(403, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Set-Cookie").isEmpty());
        assertEquals(403, origemNula.statusCode());
    }

    @Test
    void recusaAcessoPorHostQueNaoSejaLocal() throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), servidor.getPorta())) {
            OutputStream saida = socket.getOutputStream();
            saida.write(("GET /login HTTP/1.1\r\nHost: site-malicioso.example:" + servidor.getPorta()
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            saida.flush();
            InputStream entrada = socket.getInputStream();
            String resposta = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(resposta.startsWith("HTTP/1.1 403"), resposta.lines().findFirst().orElse(""));
        }
    }

    @Test
    void serveFolhaDeEstiloERespondeRotaDesconhecidaComPaginaDeErro() throws Exception {
        HttpResponse<String> css = get("/static/css/app.css", null);
        HttpResponse<String> inexistente = get("/nao-existe", null);
        HttpResponse<String> metodo = enviar(HttpRequest.newBuilder(URI.create(base + "/dashboard"))
                .DELETE());

        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
        assertTrue(css.body().contains(".faixa-alerta"));
        assertEquals(404, inexistente.statusCode());
        assertTrue(inexistente.body().contains("Página não encontrada"));
        HttpResponse<String> metodoLogin = enviar(HttpRequest.newBuilder(URI.create(base + "/login"))
                .PUT(HttpRequest.BodyPublishers.noBody()));
        assertEquals(405, metodo.statusCode());
        assertEquals("GET", metodo.headers().firstValue("Allow").orElse(""));
        assertEquals(405, metodoLogin.statusCode());
        assertEquals("GET, POST", metodoLogin.headers().firstValue("Allow").orElse(""));
    }

    private String entrar() throws Exception {
        HttpResponse<String> resposta = postarLogin(EMAIL, SENHA, base);
        assertEquals(303, resposta.statusCode());
        assertEquals("/dashboard", resposta.headers().firstValue("Location").orElse(""));
        String setCookie = resposta.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private HttpResponse<String> postarLogin(String email, String senha, String origem) throws Exception {
        String corpo = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&senha=" + URLEncoder.encode(senha, StandardCharsets.UTF_8);
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        if (origem != null) {
            requisicao.header("Origin", origem);
        }
        return enviar(requisicao);
    }

    private HttpResponse<String> get(String caminho, String cookie) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create(base + caminho)).GET();
        if (cookie != null) {
            requisicao.header("Cookie", cookie);
        }
        return enviar(requisicao);
    }

    private HttpResponse<String> enviar(HttpRequest.Builder requisicao) throws Exception {
        return cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Relogio controlado pelo teste para simular a passagem do tempo. */
    private static final class RelogioAjustavel extends Clock {
        private volatile Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
