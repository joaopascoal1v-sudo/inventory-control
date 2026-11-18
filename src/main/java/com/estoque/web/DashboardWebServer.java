package com.estoque.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.estoque.entities.Fornecedor;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.entities.Usuario;
import com.estoque.service.UsuarioService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor das telas web (Thymeleaf) do controle de estoque: login, dashboard,
 * produtos com alerta de estoque minimo e historico de movimentacoes.
 * Atende somente no endereco de loopback, assim como a API.
 */
public final class DashboardWebServer implements AutoCloseable {

    private static final Locale LOCALE = Locale.forLanguageTag("pt-BR");
    private static final Duration INATIVIDADE_SESSAO = Duration.ofMinutes(30);
    private static final int LIMITE_FORMULARIO = 8 * 1024;
    private static final int LIMITE_BUSCA = 100;
    private static final String CSP = "default-src 'none'; style-src 'self'; img-src 'self' data:; "
            + "form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
    private static final Set<String> HOSTS_PERMITIDOS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final Map<String, String> ARQUIVOS_ESTATICOS =
            Map.of("/static/css/app.css", "text/css; charset=utf-8");

    private final DashboardService painel;
    private final UsuarioService usuarios;
    private final SessoesWeb sessoes;
    private final TemplateEngine templates;
    private final ExecutorService executor;
    private final HttpServer server;
    private final boolean modoRestritoLocal;
    private Process processoTunelWeb;
    private volatile String urlPublicaTunel;

    public DashboardWebServer(DashboardService painel, UsuarioService usuarios, int porta) throws IOException {
        this(painel, usuarios, porta, Clock.systemDefaultZone(), false);
    }

    DashboardWebServer(DashboardService painel, UsuarioService usuarios, int porta, Clock relogio)
            throws IOException {
        this(painel, usuarios, porta, relogio, true);
    }

    private DashboardWebServer(
            DashboardService painel,
            UsuarioService usuarios,
            int porta,
            Clock relogio,
            boolean modoRestritoLocal
    ) throws IOException {
        if (painel == null || usuarios == null || porta < 0 || porta > 65535) {
            throw new IllegalArgumentException("Configuracao invalida para o painel web.");
        }
        this.painel = painel;
        this.usuarios = usuarios;
        this.modoRestritoLocal = modoRestritoLocal;
        this.sessoes = new SessoesWeb(INATIVIDADE_SESSAO, relogio);
        this.templates = criarMotorTemplates();
        InetSocketAddress endereco = modoRestritoLocal
                ? new InetSocketAddress(InetAddress.getLoopbackAddress(), porta)
                : new InetSocketAddress("0.0.0.0", porta);
        this.server = HttpServer.create(endereco, 0);
        this.executor = Executors.newFixedThreadPool(4, tarefa -> {
            Thread thread = new Thread(tarefa, "painel-web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::atender);
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
        executor.shutdown();
    }

    private void atender(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                rotear(exchange);
            } catch (RequisicaoRecusada e) {
                responderErro(exchange, e.status, e.getMessage());
            } catch (SQLException e) {
                System.err.println("Painel web: erro ao acessar o banco de dados: " + e.getMessage());
                responderErro(exchange, 500,
                        "Não foi possível acessar o banco de dados. Tente novamente em instantes.");
            } catch (RuntimeException e) {
                System.err.println("Painel web: erro inesperado: " + e);
                responderErro(exchange, 500, "Ocorreu um erro inesperado ao montar a página.");
            }
        }
    }

    private void rotear(HttpExchange exchange) throws IOException, SQLException {
        if (!hostPermitido(exchange)) {
            throw new RequisicaoRecusada(403, "Endereço de acesso não permitido.");
        }
        String caminho = exchange.getRequestURI().getPath();
        String metodo = exchange.getRequestMethod();

        if (ARQUIVOS_ESTATICOS.containsKey(caminho)) {
            exigirMetodo(exchange, "GET");
            servirArquivoEstatico(exchange, caminho);
            return;
        }

        switch (caminho) {
            case "/" -> {
                exigirMetodo(exchange, "GET");
                redirecionar(exchange, "/dashboard");
            }
            case "/login" -> {
                exigirMetodo(exchange, "GET", "POST");
                if ("POST".equals(metodo)) {
                    autenticar(exchange);
                } else {
                    exibirLogin(exchange);
                }
            }
            case "/cadastro" -> {
                exigirMetodo(exchange, "GET", "POST");
                if ("POST".equals(metodo)) {
                    registrarNovoUsuario(exchange);
                } else {
                    exibirCadastro(exchange);
                }
            }
            case "/logout" -> {
                exigirMetodo(exchange, "POST");
                sair(exchange);
            }
            case "/dashboard", "/produtos", "/movimentacoes", "/fornecedores",
                    "/produtos/movimentar", "/produtos/remover", "/movimentacoes/estornar",
                    "/relatorios/pdf", "/relatorios/excel", "/servidor/tunel" -> {
                if ("/dashboard".equals(caminho)
                        || "/relatorios/pdf".equals(caminho)
                        || "/relatorios/excel".equals(caminho)) {
                    exigirMetodo(exchange, "GET");
                } else if ("/produtos/movimentar".equals(caminho)
                        || "/produtos/remover".equals(caminho)
                        || "/movimentacoes/estornar".equals(caminho)
                        || "/servidor/tunel".equals(caminho)) {
                    exigirMetodo(exchange, "POST");
                } else {
                    exigirMetodo(exchange, "GET", "POST");
                }
                Optional<Usuario> usuario = usuarioDaSessao(exchange);
                if (usuario.isEmpty()) {
                    String token = lerCookie(exchange, SessoesWeb.COOKIE);
                    redirecionar(exchange, token == null ? "/login" : "/login?expirou");
                    return;
                }
                if ("POST".equals(metodo)) {
                    switch (caminho) {
                        case "/produtos" -> salvarProdutoWeb(exchange, usuario.get());
                        case "/produtos/movimentar" -> movimentarRapidoWeb(exchange, usuario.get());
                        case "/produtos/remover" -> removerProdutoWeb(exchange);
                        case "/movimentacoes" -> salvarMovimentacaoWeb(exchange, usuario.get());
                        case "/movimentacoes/estornar" -> estornarMovimentacaoWeb(exchange, usuario.get());
                        case "/fornecedores" -> salvarFornecedorWeb(exchange);
                        case "/servidor/tunel" -> ativarTunelPublicoWeb(exchange, usuario.get());
                        default -> throw new RequisicaoRecusada(404, "Rota não encontrada.");
                    }
                    return;
                }
                switch (caminho) {
                    case "/dashboard" -> exibirDashboard(exchange, usuario.get());
                    case "/produtos" -> exibirProdutos(exchange, usuario.get());
                    case "/fornecedores" -> exibirFornecedores(exchange, usuario.get());
                    case "/relatorios/pdf" -> baixarRelatorioPdf(exchange);
                    case "/relatorios/excel" -> baixarRelatorioExcel(exchange);
                    default -> exibirMovimentacoes(exchange, usuario.get());
                }
            }
            default -> throw new RequisicaoRecusada(404, "A página solicitada não existe.");
        }
    }

    // ----- Autenticacao -------------------------------------------------------------------

    private void exibirLogin(HttpExchange exchange) throws IOException, SQLException {
        if (usuarioDaSessao(exchange).isPresent()) {
            redirecionar(exchange, "/dashboard");
            return;
        }
        if (!modoRestritoLocal && !usuarios.temUsuarios()) {
            exibirCadastro(exchange);
            return;
        }
        Map<String, String> consulta = lerConsulta(exchange);
        Map<String, Object> modelo = modeloLogin("");
        modelo.put("saiu", consulta.containsKey("saiu"));
        modelo.put("expirou", consulta.containsKey("expirou"));
        renderizar(exchange, 200, "login", modelo);
    }

    private void autenticar(HttpExchange exchange) throws IOException, SQLException {
        exigirMesmaOrigem(exchange);
        Map<String, String> formulario = lerFormulario(exchange);
        String email = formulario.getOrDefault("email", "").trim();
        String senha = formulario.getOrDefault("senha", "");
        Usuario usuario;
        try {
            usuario = usuarios.login(email, senha);
        } catch (IllegalArgumentException e) {
            Map<String, Object> modelo = modeloLogin(email);
            modelo.put("erro", "E-mail ou senha inválidos.");
            renderizar(exchange, 401, "login", modelo);
            return;
        }
        sessoes.encerrar(lerCookie(exchange, SessoesWeb.COOKIE));
        String token = sessoes.criar(usuario);
        exchange.getResponseHeaders().add("Set-Cookie",
                SessoesWeb.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        redirecionar(exchange, "/dashboard");
    }

    private void exibirCadastro(HttpExchange exchange) throws IOException, SQLException {
        if (usuarioDaSessao(exchange).isPresent()) {
            redirecionar(exchange, "/dashboard");
            return;
        }
        Map<String, Object> modelo = modeloLogin("");
        modelo.put("modoCadastro", true);
        modelo.put("nomeCadastro", "");
        modelo.put("emailCadastro", "");
        renderizar(exchange, 200, "login", modelo);
    }

    private void registrarNovoUsuario(HttpExchange exchange) throws IOException, SQLException {
        exigirMesmaOrigem(exchange);
        Map<String, String> formulario = lerFormulario(exchange);
        String nome = formulario.getOrDefault("nome", "").trim();
        String email = formulario.getOrDefault("email", "").trim();
        String senha = formulario.getOrDefault("senha", "");
        String confirmarSenha = formulario.getOrDefault("confirmarSenha", "");

        if (!senha.equals(confirmarSenha)) {
            Map<String, Object> modelo = modeloLogin("");
            modelo.put("modoCadastro", true);
            modelo.put("nomeCadastro", nome);
            modelo.put("emailCadastro", email);
            modelo.put("erroCadastro", "As senhas informadas não conferem.");
            renderizar(exchange, 400, "login", modelo);
            return;
        }

        Usuario novoUsuario;
        try {
            novoUsuario = usuarios.registrarAutoCadastro(nome, email, senha);
            if (!modoRestritoLocal && novoUsuario.getPerfil() == PerfilAcesso.ADMIN) {
                painel.popularEstoqueFarmaciaSeVazio(novoUsuario.getId());
            }
        } catch (IllegalArgumentException e) {
            Map<String, Object> modelo = modeloLogin("");
            modelo.put("modoCadastro", true);
            modelo.put("nomeCadastro", nome);
            modelo.put("emailCadastro", email);
            modelo.put("erroCadastro", e.getMessage());
            renderizar(exchange, 400, "login", modelo);
            return;
        }

        sessoes.encerrar(lerCookie(exchange, SessoesWeb.COOKIE));
        String token = sessoes.criar(novoUsuario);
        exchange.getResponseHeaders().add("Set-Cookie",
                SessoesWeb.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        redirecionar(exchange, "/dashboard");
    }

    private void salvarProdutoWeb(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            String codigo = form.getOrDefault("codigo", "").trim();
            String nome = form.getOrDefault("nome", "").trim();
            BigDecimal preco = new BigDecimal(form.getOrDefault("preco", "0").trim().replace(',', '.'));
            int quantidade = Integer.parseInt(form.getOrDefault("quantidade", "0").trim());
            String minStr = form.getOrDefault("estoqueMinimo", "0").trim();
            int estoqueMinimo = minStr.isEmpty() ? 0 : Integer.parseInt(minStr);
            painel.cadastrarProduto(codigo, nome, preco, quantidade, estoqueMinimo);
            redirecionar(exchange, "/produtos?cadastrado");
        } catch (Exception e) {
            List<ProdutoView> todos = painel.listarProdutos();
            List<ProdutoView> alertas = todos.stream().filter(ProdutoView::emAlerta).toList();
            Map<String, Object> modelo = modeloAutenticado(usuario, "produtos", alertas);
            modelo.put("produtos", todos);
            modelo.put("somenteAlertas", false);
            modelo.put("busca", "");
            modelo.put("totalProdutos", todos.size());
            modelo.put("erroCadastroProduto", e instanceof NumberFormatException
                    ? "Informe preço e quantidade válidos."
                    : e.getMessage());
            renderizar(exchange, 400, "produtos", modelo);
        }
    }

    private void salvarMovimentacaoWeb(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            String codigoProduto = form.getOrDefault("codigoProduto", "").trim();
            TipoMovimentacao tipo = TipoMovimentacao.valueOf(
                    form.getOrDefault("tipo", "ENTRADA").trim().toUpperCase(Locale.ROOT));
            int quantidade = Integer.parseInt(form.getOrDefault("quantidade", "0").trim());
            MotivoMovimentacao motivo = tipo == TipoMovimentacao.ENTRADA
                    ? MotivoMovimentacao.COMPRA
                    : MotivoMovimentacao.VENDA;
            painel.registrarMovimentacao(codigoProduto, tipo, quantidade, motivo, usuario.getId());
            redirecionar(exchange, "/movimentacoes?registrado");
        } catch (Exception e) {
            redirecionar(exchange, "/movimentacoes?erro=" + java.net.URLEncoder.encode(
                    e instanceof NumberFormatException ? "Informe uma quantidade válida." : e.getMessage(),
                    StandardCharsets.UTF_8));
        }
    }

    private void sair(HttpExchange exchange) throws IOException {
        exigirMesmaOrigem(exchange);
        sessoes.encerrar(lerCookie(exchange, SessoesWeb.COOKIE));
        exchange.getResponseHeaders().add("Set-Cookie",
                SessoesWeb.COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        redirecionar(exchange, "/login?saiu");
    }

    private Map<String, Object> modeloLogin(String email) throws SQLException {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("email", email);
        modelo.put("semUsuarios", !usuarios.temUsuarios());
        return modelo;
    }

    private Optional<Usuario> usuarioDaSessao(HttpExchange exchange) {
        return sessoes.buscar(lerCookie(exchange, SessoesWeb.COOKIE));
    }

    // ----- Paginas autenticadas -------------------------------------------------------------

    private void exibirDashboard(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        ResumoDashboard resumo = painel.montarResumo();
        Map<String, Object> modelo = modeloAutenticado(usuario, "dashboard", resumo.alertas());
        modelo.put("resumo", resumo);
        modelo.put("isAdmin", usuario.getPerfil() == PerfilAcesso.ADMIN);
        modelo.put("linkRedeLocal", obterLinkRedeLocal());
        modelo.put("linkInternetPublico", urlPublicaTunel);
        renderizar(exchange, 200, "dashboard", modelo);
    }

    private String obterLinkRedeLocal() {
        String ip = "localhost";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }
        return "http://" + ip + ":" + getPorta() + "/login";
    }

    private synchronized void ativarTunelPublicoWeb(HttpExchange exchange, Usuario usuario) throws IOException {
        exigirMesmaOrigem(exchange);
        if (usuario.getPerfil() != PerfilAcesso.ADMIN) {
            redirecionar(exchange, "/dashboard");
            return;
        }
        if (urlPublicaTunel == null) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ssh",
                        "-o", "StrictHostKeyChecking=accept-new",
                        "-o", "ServerAliveInterval=30",
                        "-R", "80:localhost:" + getPorta(),
                        "nokey@localhost.run");
                pb.redirectErrorStream(true);
                processoTunelWeb = pb.start();
                Pattern padrao = Pattern.compile("(https://[a-zA-Z0-9.-]+\\.lhr\\.life)");
                long limite = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(8);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(processoTunelWeb.getInputStream(), StandardCharsets.UTF_8));
                while (System.currentTimeMillis() < limite) {
                    if (reader.ready()) {
                        String linha = reader.readLine();
                        if (linha == null) {
                            break;
                        }
                        Matcher m = padrao.matcher(linha);
                        if (m.find()) {
                            urlPublicaTunel = m.group(1) + "/login";
                            break;
                        }
                    } else {
                        Thread.sleep(150);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        redirecionar(exchange, "/dashboard");
    }

    private void exibirProdutos(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        Map<String, String> consulta = lerConsulta(exchange);
        boolean somenteAlertas = "baixo".equals(consulta.get("situacao"));
        String busca = consulta.getOrDefault("busca", "").trim();
        if (busca.length() > LIMITE_BUSCA) {
            busca = busca.substring(0, LIMITE_BUSCA);
        }

        List<ProdutoView> todos = painel.listarProdutos();
        List<ProdutoView> alertas = todos.stream().filter(ProdutoView::emAlerta).toList();

        Map<String, Object> modelo = modeloAutenticado(usuario, "produtos", alertas);
        modelo.put("produtos", DashboardService.filtrarProdutos(todos, somenteAlertas, busca));
        modelo.put("somenteAlertas", somenteAlertas);
        modelo.put("busca", busca);
        modelo.put("totalProdutos", todos.size());
        modelo.put("cadastrado", consulta.containsKey("cadastrado"));
        modelo.put("movimentado", consulta.containsKey("movimentado"));
        modelo.put("removido", consulta.containsKey("removido"));
        if (consulta.containsKey("erro") && !consulta.get("erro").isBlank()) {
            modelo.put("erroCadastroProduto", consulta.get("erro"));
        }
        renderizar(exchange, 200, "produtos", modelo);
    }

    private void movimentarRapidoWeb(HttpExchange exchange, Usuario usuario) throws IOException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            String codigo = form.getOrDefault("codigo", "").trim();
            TipoMovimentacao tipo = TipoMovimentacao.valueOf(
                    form.getOrDefault("tipo", "ENTRADA").trim().toUpperCase(Locale.ROOT));
            int quantidade = Integer.parseInt(form.getOrDefault("quantidade", "1").trim());
            MotivoMovimentacao motivo = tipo == TipoMovimentacao.ENTRADA
                    ? MotivoMovimentacao.COMPRA
                    : MotivoMovimentacao.VENDA;
            painel.registrarMovimentacao(codigo, tipo, quantidade, motivo, usuario.getId());
            redirecionar(exchange, "/produtos?movimentado");
        } catch (Exception e) {
            redirecionar(exchange, "/produtos?erro=" + java.net.URLEncoder.encode(
                    e.getMessage() == null ? "Não foi possível movimentar o produto." : e.getMessage(),
                    StandardCharsets.UTF_8));
        }
    }

    private void removerProdutoWeb(HttpExchange exchange) throws IOException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            String codigo = form.getOrDefault("codigo", "").trim();
            painel.removerProduto(codigo);
            redirecionar(exchange, "/produtos?removido");
        } catch (Exception e) {
            redirecionar(exchange, "/produtos?erro=" + java.net.URLEncoder.encode(
                    e.getMessage() == null ? "Não foi possível remover o produto (possui movimentações vinculadas)." : e.getMessage(),
                    StandardCharsets.UTF_8));
        }
    }

    private void estornarMovimentacaoWeb(HttpExchange exchange, Usuario usuario) throws IOException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            long id = Long.parseLong(form.getOrDefault("id", "0").trim());
            painel.estornarMovimentacao(id, usuario.getId());
            redirecionar(exchange, "/movimentacoes?estornado");
        } catch (Exception e) {
            redirecionar(exchange, "/movimentacoes?erro=" + java.net.URLEncoder.encode(
                    e.getMessage() == null ? "Não foi possível estornar a movimentação." : e.getMessage(),
                    StandardCharsets.UTF_8));
        }
    }

    private void exibirFornecedores(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        Map<String, String> consulta = lerConsulta(exchange);
        List<ProdutoView> alertas = painel.listarProdutos().stream().filter(ProdutoView::emAlerta).toList();
        Map<String, Object> modelo = modeloAutenticado(usuario, "fornecedores", alertas);
        modelo.put("fornecedores", painel.listarFornecedores());
        modelo.put("cadastrado", consulta.containsKey("cadastrado"));
        if (consulta.containsKey("erro") && !consulta.get("erro").isBlank()) {
            modelo.put("erro", consulta.get("erro"));
        }
        renderizar(exchange, 200, "fornecedores", modelo);
    }

    private void salvarFornecedorWeb(HttpExchange exchange) throws IOException {
        exigirMesmaOrigem(exchange);
        Map<String, String> form = lerFormulario(exchange);
        try {
            painel.cadastrarFornecedor(new Fornecedor(
                    form.getOrDefault("nome", ""),
                    form.getOrDefault("cnpj", ""),
                    form.getOrDefault("telefone", ""),
                    form.getOrDefault("email", ""),
                    form.getOrDefault("endereco", "")));
            redirecionar(exchange, "/fornecedores?cadastrado");
        } catch (Exception e) {
            redirecionar(exchange, "/fornecedores?erro=" + java.net.URLEncoder.encode(
                    e.getMessage() == null ? "Dados inválidos para o fornecedor." : e.getMessage(),
                    StandardCharsets.UTF_8));
        }
    }

    private void baixarRelatorioPdf(HttpExchange exchange) throws IOException, SQLException {
        byte[] pdf = painel.gerarPdfRelatorio();
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"relatorio-estoque.pdf\"");
        enviar(exchange, 200, "application/pdf", pdf);
    }

    private void baixarRelatorioExcel(HttpExchange exchange) throws IOException, SQLException {
        byte[] xlsx = painel.gerarExcelRelatorio();
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"relatorio-estoque.xlsx\"");
        enviar(exchange, 200, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
    }

    private void exibirMovimentacoes(HttpExchange exchange, Usuario usuario) throws IOException, SQLException {
        Map<String, String> consulta = lerConsulta(exchange);
        FiltroMovimentacoes filtro = new FiltroMovimentacoes(
                consulta.getOrDefault("produto", "").trim(),
                consulta.getOrDefault("tipo", "").trim(),
                consulta.getOrDefault("inicio", "").trim(),
                consulta.getOrDefault("fim", "").trim());

        List<String> erros = new ArrayList<>();
        TipoMovimentacao tipo = lerTipo(filtro.tipo(), erros);
        LocalDate inicio = lerData(filtro.inicio(), "inicial", erros);
        LocalDate fim = lerData(filtro.fim(), "final", erros);
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            erros.add("A data inicial não pode ser posterior à data final.");
        }
        if (consulta.containsKey("erro") && !consulta.get("erro").isBlank()) {
            erros.add(consulta.get("erro"));
        }

        List<ProdutoView> produtos = painel.listarProdutos();
        HistoricoMovimentacoes historico = erros.isEmpty()
                ? painel.consultarHistorico(produtos, filtro.produto(), tipo, inicio, fim)
                : new HistoricoMovimentacoes(List.of(), 0);

        Map<String, Object> modelo = modeloAutenticado(usuario, "movimentacoes",
                produtos.stream().filter(ProdutoView::emAlerta).toList());
        modelo.put("filtro", filtro);
        modelo.put("filtrando", filtro.ativo());
        modelo.put("produtos", produtos);
        modelo.put("tipos", Rotulos.tiposMovimentacao());
        modelo.put("erros", erros);
        modelo.put("registrado", consulta.containsKey("registrado"));
        modelo.put("estornado", consulta.containsKey("estornado"));
        modelo.put("historico", historico);
        modelo.put("limiteHistorico", DashboardService.LIMITE_HISTORICO);
        renderizar(exchange, 200, "movimentacoes", modelo);
    }

    private Map<String, Object> modeloAutenticado(Usuario usuario, String paginaAtiva, List<ProdutoView> alertas) {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("usuarioNome", usuario.getNome());
        modelo.put("usuarioPerfil", Rotulos.perfil(usuario.getPerfil()));
        modelo.put("paginaAtiva", paginaAtiva);
        modelo.put("totalAlertas", alertas.size());
        modelo.put("alertasSemEstoque",
                alertas.stream().filter(p -> p.situacao() == SituacaoEstoque.ESGOTADO).count());
        modelo.put("geradoEm", LocalDateTime.now());
        return modelo;
    }

    private TipoMovimentacao lerTipo(String valor, List<String> erros) {
        if (valor.isEmpty()) {
            return null;
        }
        try {
            return TipoMovimentacao.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            erros.add("Tipo de movimentação inválido.");
            return null;
        }
    }

    private LocalDate lerData(String valor, String descricao, List<String> erros) {
        if (valor.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            erros.add("A data " + descricao + " é inválida. Use o formato AAAA-MM-DD.");
            return null;
        }
    }

    // ----- Respostas ------------------------------------------------------------------------

    private void renderizar(HttpExchange exchange, int status, String template, Map<String, Object> modelo)
            throws IOException {
        String html = templates.process(template, new Context(LOCALE, modelo));
        enviar(exchange, status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void responderErro(HttpExchange exchange, int status, String mensagem) throws IOException {
        Map<String, Object> modelo = new HashMap<>();
        modelo.put("status", status);
        modelo.put("titulo", switch (status) {
            case 403 -> "Acesso negado";
            case 404 -> "Página não encontrada";
            case 405 -> "Método não permitido";
            case 413 -> "Requisição muito grande";
            case 500 -> "Erro no servidor";
            default -> "Requisição inválida";
        });
        modelo.put("mensagem", mensagem);
        renderizar(exchange, status, "erro", modelo);
    }

    private void servirArquivoEstatico(HttpExchange exchange, String caminho) throws IOException {
        byte[] conteudo;
        try (InputStream entrada = DashboardWebServer.class.getClassLoader()
                .getResourceAsStream(caminho.substring(1))) {
            if (entrada == null) {
                throw new RequisicaoRecusada(404, "Arquivo não encontrado.");
            }
            conteudo = entrada.readAllBytes();
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        enviar(exchange, 200, ARQUIVOS_ESTATICOS.get(caminho), conteudo);
    }

    private void redirecionar(HttpExchange exchange, String destino) throws IOException {
        exchange.getResponseHeaders().set("Location", destino);
        enviar(exchange, 303, null, new byte[0]);
    }

    private void enviar(HttpExchange exchange, int status, String tipo, byte[] corpo) throws IOException {
        Headers cabecalhos = exchange.getResponseHeaders();
        if (tipo != null) {
            cabecalhos.set("Content-Type", tipo);
        }
        if (!cabecalhos.containsKey("Cache-Control")) {
            cabecalhos.set("Cache-Control", "no-store");
        }
        cabecalhos.set("Content-Security-Policy", CSP);
        cabecalhos.set("X-Content-Type-Options", "nosniff");
        cabecalhos.set("X-Frame-Options", "DENY");
        // "no-referrer" faria o navegador enviar "Origin: null" nos formularios do proprio
        // painel, o que a verificacao de origem (CSRF) recusaria.
        cabecalhos.set("Referrer-Policy", "same-origin");
        exchange.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        if (corpo.length > 0) {
            exchange.getResponseBody().write(corpo);
        }
    }

    // ----- Leitura da requisicao ------------------------------------------------------------

    private void exigirMetodo(HttpExchange exchange, String... metodos) {
        if (!List.of(metodos).contains(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", metodos));
            throw new RequisicaoRecusada(405, "Método " + exchange.getRequestMethod() + " não é aceito aqui.");
        }
    }

    /**
     * Impede requisicoes de outros enderecos (DNS rebinding): o navegador precisa ter
     * acessado o painel por localhost, 127.0.0.1 ou [::1].
     */
    private boolean hostPermitido(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        if (!modoRestritoLocal) {
            return true;
        }
        String nome = host;
        if (host.startsWith("[")) {
            int fim = host.indexOf(']');
            nome = fim < 0 ? host : host.substring(0, fim + 1);
        } else if (host.lastIndexOf(':') >= 0) {
            nome = host.substring(0, host.lastIndexOf(':'));
        }
        return HOSTS_PERMITIDOS.contains(nome.toLowerCase(Locale.ROOT));
    }

    /** Recusa formularios enviados por paginas de outra origem (CSRF). */
    private void exigirMesmaOrigem(HttpExchange exchange) {
        String origem = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (origem == null) {
            return;
        }
        boolean mesmaOrigemHttp = origem.equals("http://" + host);
        boolean mesmaOrigemHttps = !modoRestritoLocal && origem.equals("https://" + host);
        if (!mesmaOrigemHttp && !mesmaOrigemHttps) {
            throw new RequisicaoRecusada(403, "Formulário enviado por uma origem não permitida.");
        }
    }

    private Map<String, String> lerFormulario(HttpExchange exchange) throws IOException {
        byte[] corpo = exchange.getRequestBody().readNBytes(LIMITE_FORMULARIO + 1);
        if (corpo.length > LIMITE_FORMULARIO) {
            throw new RequisicaoRecusada(413, "O formulário enviado excede o tamanho permitido.");
        }
        return decodificar(new String(corpo, StandardCharsets.UTF_8));
    }

    private Map<String, String> lerConsulta(HttpExchange exchange) {
        return decodificar(exchange.getRequestURI().getRawQuery());
    }

    private Map<String, String> decodificar(String dados) {
        Map<String, String> valores = new HashMap<>();
        if (dados == null || dados.isBlank()) {
            return valores;
        }
        try {
            for (String parte : dados.split("&")) {
                if (parte.isEmpty()) {
                    continue;
                }
                String[] chaveValor = parte.split("=", 2);
                valores.put(URLDecoder.decode(chaveValor[0], StandardCharsets.UTF_8),
                        chaveValor.length == 2 ? URLDecoder.decode(chaveValor[1], StandardCharsets.UTF_8) : "");
            }
        } catch (IllegalArgumentException e) {
            throw new RequisicaoRecusada(400, "Parâmetros da requisição inválidos.");
        }
        return valores;
    }

    private String lerCookie(HttpExchange exchange, String nome) {
        List<String> cabecalhos = exchange.getRequestHeaders().get("Cookie");
        if (cabecalhos == null) {
            return null;
        }
        for (String cabecalho : cabecalhos) {
            for (String cookie : cabecalho.split(";")) {
                String[] chaveValor = cookie.trim().split("=", 2);
                if (chaveValor.length == 2 && chaveValor[0].equals(nome) && !chaveValor[1].isEmpty()) {
                    return chaveValor[1];
                }
            }
        }
        return null;
    }

    private static TemplateEngine criarMotorTemplates() {
        ClassLoaderTemplateResolver resolver =
                new ClassLoaderTemplateResolver(DashboardWebServer.class.getClassLoader());
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new LinkBuilderRaiz());
        return engine;
    }

    /** Permite links absolutos como @{/produtos} fora de um container de servlets. */
    private static final class LinkBuilderRaiz extends StandardLinkBuilder {
        @Override
        protected String computeContextPath(
                IExpressionContext context, String base, Map<String, Object> parameters) {
            return "";
        }
    }

    /** Valores dos filtros da tela de movimentacoes, como digitados pelo usuario. */
    public record FiltroMovimentacoes(String produto, String tipo, String inicio, String fim) {
        public boolean ativo() {
            return !produto.isEmpty() || !tipo.isEmpty() || !inicio.isEmpty() || !fim.isEmpty();
        }
    }

    private static final class RequisicaoRecusada extends RuntimeException {
        private final int status;

        RequisicaoRecusada(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }
}
