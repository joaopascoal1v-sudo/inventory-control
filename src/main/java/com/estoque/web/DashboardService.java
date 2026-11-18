package com.estoque.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.estoque.entities.Fornecedor;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.Produto;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.repository.FornecedorRepositoryJdbc;
import com.estoque.service.EstoqueService;
import com.estoque.service.FornecedorService;
import com.estoque.service.RelatorioExportacaoService;

/** Monta os dados das telas web a partir das regras de {@link EstoqueService}. */
public class DashboardService {

    public static final int LIMITE_ULTIMAS_MOVIMENTACOES = 10;
    public static final int LIMITE_HISTORICO = 200;

    private final EstoqueService estoque;
    private final FornecedorService fornecedores;
    private final RelatorioExportacaoService exportacao;

    public DashboardService(EstoqueService estoque) {
        this(estoque, new FornecedorService(new FornecedorRepositoryJdbc()));
    }

    public DashboardService(EstoqueService estoque, FornecedorService fornecedores) {
        if (estoque == null) {
            throw new IllegalArgumentException("O servico de estoque e obrigatorio.");
        }
        this.estoque = estoque;
        this.fornecedores = fornecedores;
        this.exportacao = new RelatorioExportacaoService(estoque);
    }

    public List<ProdutoView> listarProdutos() throws SQLException {
        return estoque.listar().stream().map(ProdutoView::de).toList();
    }

    public void cadastrarProduto(
            String codigo,
            String nome,
            BigDecimal preco,
            int quantidade,
            int estoqueMinimo
    ) throws SQLException {
        estoque.cadastrar(new Produto(codigo, nome, preco, quantidade, estoqueMinimo));
    }

    public void registrarMovimentacao(
            String codigoProduto,
            TipoMovimentacao tipo,
            int quantidade,
            MotivoMovimentacao motivo,
            Long usuarioId
    ) throws SQLException {
        estoque.registrarMovimentacao(codigoProduto, tipo, null, quantidade, motivo, usuarioId, null);
    }

    public void removerProduto(String codigo) throws SQLException {
        estoque.remover(codigo);
    }

    public void estornarMovimentacao(long id, Long usuarioId) throws SQLException {
        estoque.estornarMovimentacao(id, usuarioId);
    }

    public List<Fornecedor> listarFornecedores() throws SQLException {
        return fornecedores.listar();
    }

    public void cadastrarFornecedor(Fornecedor fornecedor) throws SQLException {
        fornecedores.cadastrar(fornecedor);
    }

    public void popularEstoqueFarmaciaSeVazio(Long adminId) throws SQLException {
        if (!estoque.listar().isEmpty()) {
            return;
        }

        estoque.cadastrar(new Produto("MED001", "Dipirona Monoidratada 500mg (10 comp.)", new BigDecimal("8.90"), 120, 25));
        estoque.cadastrar(new Produto("MED002", "Dipirona Gotas 500mg/ml Frasco 20ml", new BigDecimal("11.50"), 65, 15));
        estoque.cadastrar(new Produto("MED003", "Paracetamol 750mg Generico (20 comp.)", new BigDecimal("14.50"), 90, 20));
        estoque.cadastrar(new Produto("MED004", "Dorflex Relaxante Muscular (36 comp.)", new BigDecimal("24.90"), 80, 20));
        estoque.cadastrar(new Produto("MED005", "Ibuprofeno 600mg (20 comp.)", new BigDecimal("22.90"), 45, 12));
        estoque.cadastrar(new Produto("MED006", "Nimesulida 100mg (12 comp.)", new BigDecimal("15.90"), 55, 15));
        estoque.cadastrar(new Produto("MED007", "Amoxicilina 500mg Capsulas (21 caps.)", new BigDecimal("34.90"), 4, 12));
        estoque.cadastrar(new Produto("MED008", "Azitromicina 500mg (5 comp.)", new BigDecimal("29.90"), 6, 10));
        estoque.cadastrar(new Produto("MED009", "Loratadina 10mg Antialergico (12 comp.)", new BigDecimal("16.80"), 5, 15));
        estoque.cadastrar(new Produto("MED010", "Allegra 120mg (10 comp.)", new BigDecimal("42.50"), 28, 8));
        estoque.cadastrar(new Produto("MED011", "Cimegripe Capsulas (20 caps.)", new BigDecimal("18.90"), 70, 20));
        estoque.cadastrar(new Produto("MED012", "Xarope Vick Mel Expectorante 120ml", new BigDecimal("36.90"), 22, 8));
        estoque.cadastrar(new Produto("MED013", "Omeprazol 20mg Protetor Gastrico (28 caps.)", new BigDecimal("19.90"), 60, 15));
        estoque.cadastrar(new Produto("MED014", "Sal de Fruta Eno Tradicional (2 env.)", new BigDecimal("5.50"), 95, 30));
        estoque.cadastrar(new Produto("MED015", "Losartana Potassica 50mg (30 comp.)", new BigDecimal("12.90"), 110, 25));
        estoque.cadastrar(new Produto("MED016", "Simeticona 125mg Antigases (10 caps.)", new BigDecimal("17.50"), 38, 10));
        estoque.cadastrar(new Produto("VIT017", "Vitamina C Redoxon 1g Efervescente (10 comp.)", new BigDecimal("26.50"), 40, 10));
        estoque.cadastrar(new Produto("VIT018", "Vitamina D3 2000UI (30 caps.)", new BigDecimal("39.90"), 34, 8));
        estoque.cadastrar(new Produto("CUR019", "Soro Fisiologico 0,9% 500ml", new BigDecimal("9.50"), 0, 15));
        estoque.cadastrar(new Produto("CUR020", "Alcool 70% Antisseptico 1 Litro", new BigDecimal("13.90"), 3, 12));
        estoque.cadastrar(new Produto("CUR021", "Curativo Band-Aid Transparente (40 un.)", new BigDecimal("19.50"), 48, 10));
        estoque.cadastrar(new Produto("DER022", "Protetor Solar Facial FPS 60 50g", new BigDecimal("69.90"), 25, 6));
        estoque.cadastrar(new Produto("INF023", "Fralda Pampers Confort Sec G (38 un.)", new BigDecimal("64.90"), 30, 8));
        estoque.cadastrar(new Produto("EQU024", "Termometro Digital Clinico G-Tech", new BigDecimal("28.00"), 16, 5));

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

    public byte[] gerarPdfRelatorio() throws SQLException, IOException {
        Path temp = Files.createTempFile("relatorio-estoque-", ".pdf");
        try {
            exportacao.exportarPdf(temp);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public byte[] gerarExcelRelatorio() throws SQLException, IOException {
        Path temp = Files.createTempFile("relatorio-estoque-", ".xlsx");
        try {
            exportacao.exportarExcel(temp);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public ResumoDashboard montarResumo() throws SQLException {
        List<ProdutoView> produtos = listarProdutos();
        List<MovimentacaoView> ultimas = comNomes(
                estoque.listarUltimasMovimentacoes(LIMITE_ULTIMAS_MOVIMENTACOES), produtos);
        return ResumoDashboard.de(produtos, ultimas);
    }

    /**
     * Consulta o historico com os filtros informados (todos opcionais), retornando
     * no maximo {@link #LIMITE_HISTORICO} registros, dos mais recentes aos mais antigos.
     */
    public HistoricoMovimentacoes consultarHistorico(
            List<ProdutoView> produtos,
            String codigoProduto,
            TipoMovimentacao tipo,
            LocalDate inicio,
            LocalDate fim) throws SQLException {
        List<MovimentacaoEstoque> encontradas = estoque.listarHistorico(codigoProduto, tipo, inicio, fim);
        List<MovimentacaoEstoque> exibidas =
                encontradas.subList(0, Math.min(LIMITE_HISTORICO, encontradas.size()));
        return new HistoricoMovimentacoes(comNomes(exibidas, produtos), encontradas.size());
    }

    /**
     * Filtra a lista de produtos da tela de produtos. Com {@code somenteAlertas}, mantem
     * apenas os produtos abaixo do minimo, ordenados do mais critico ao menos critico.
     * A busca compara o texto com codigo e nome, sem diferenciar maiusculas.
     */
    public static List<ProdutoView> filtrarProdutos(
            List<ProdutoView> produtos, boolean somenteAlertas, String busca) {
        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        Stream<ProdutoView> resultado = produtos.stream();
        if (somenteAlertas) {
            resultado = resultado.filter(ProdutoView::emAlerta).sorted(ProdutoView.POR_GRAVIDADE);
        }
        if (!termo.isEmpty()) {
            resultado = resultado.filter(p -> p.codigo().toLowerCase(Locale.ROOT).contains(termo)
                    || p.nome().toLowerCase(Locale.ROOT).contains(termo));
        }
        return resultado.toList();
    }

    private List<MovimentacaoView> comNomes(List<MovimentacaoEstoque> movimentos, List<ProdutoView> produtos) {
        Map<String, String> nomes = new HashMap<>();
        for (ProdutoView produto : produtos) {
            nomes.put(produto.codigo(), produto.nome());
        }
        return movimentos.stream()
                .map(m -> MovimentacaoView.de(m, nomes.getOrDefault(m.codigoProduto(), m.codigoProduto())))
                .toList();
    }
}
