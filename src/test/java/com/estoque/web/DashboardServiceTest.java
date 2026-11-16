package com.estoque.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.Produto;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.service.EstoqueService;

class DashboardServiceTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 1, 8, 0);

    private RepositoriosEmMemoria.Produtos produtos;
    private RepositoriosEmMemoria.Movimentacoes movimentacoes;
    private EstoqueService estoque;
    private DashboardService painel;

    @BeforeEach
    void preparar() {
        produtos = new RepositoriosEmMemoria.Produtos();
        movimentacoes = new RepositoriosEmMemoria.Movimentacoes();
        estoque = new EstoqueService(produtos, movimentacoes);
        painel = new DashboardService(estoque);

        produtos.inserir(new Produto("A1", "Arruela", new BigDecimal("0.50"), 100, 20));
        produtos.inserir(new Produto("B2", "Broca", new BigDecimal("12.90"), 0, 5));
        produtos.inserir(new Produto("C3", "Cola", new BigDecimal("8.00"), 3, 10));
        produtos.inserir(new Produto("D4", "Disco", new BigDecimal("30.00"), 0, 0));
    }

    @Test
    void montaResumoComTotaisEAlertasDoMaisCriticoParaOMenosCritico() throws SQLException {
        ResumoDashboard resumo = painel.montarResumo();

        assertEquals(4, resumo.totalProdutos());
        assertEquals(103, resumo.unidadesEmEstoque());
        assertEquals(0, new BigDecimal("74.00").compareTo(resumo.valorEmEstoque()));
        assertEquals(List.of("B2", "C3"), resumo.alertas().stream().map(ProdutoView::codigo).toList());
        assertEquals(1, resumo.produtosSemEstoque());
        assertEquals(SituacaoEstoque.ESGOTADO, resumo.alertas().get(0).situacao());
        assertEquals(SituacaoEstoque.BAIXO, resumo.alertas().get(1).situacao());
    }

    @Test
    void alertasDoPainelSeguemAMesmaRegraDoServicoDeEstoque() throws SQLException {
        produtos.inserir(new Produto("E5", "Engrenagem", BigDecimal.ONE, 7, 7));

        List<String> doServico = estoque.listarAlertasEstoqueMinimo().stream()
                .map(Produto::getCodigo).sorted().toList();
        List<String> doPainel = painel.montarResumo().alertas().stream()
                .map(ProdutoView::codigo).sorted().toList();

        assertEquals(doServico, doPainel);
        assertEquals(SituacaoEstoque.NORMAL, SituacaoEstoque.de(produtos.buscarPorCodigo("E5").orElseThrow()));
        assertEquals(SituacaoEstoque.NORMAL, SituacaoEstoque.de(produtos.buscarPorCodigo("D4").orElseThrow()));
    }

    @Test
    void ultimasMovimentacoesTrazemAsMaisRecentesComNomeDoProduto() throws SQLException {
        for (int i = 0; i < 12; i++) {
            movimentacoes.adicionar(i % 2 == 0 ? "A1" : "C3", TipoMovimentacao.ENTRADA,
                    DirecaoMovimentacao.ENTRADA, 1, i, INICIO.plusMinutes(i));
        }

        List<MovimentacaoView> ultimas = painel.montarResumo().ultimasMovimentacoes();

        assertEquals(DashboardService.LIMITE_ULTIMAS_MOVIMENTACOES, ultimas.size());
        assertEquals(12, ultimas.get(0).id());
        assertEquals("Cola", ultimas.get(0).nomeProduto());
        assertEquals("Arruela", ultimas.get(1).nomeProduto());
        assertEquals(3, ultimas.getLast().id());
    }

    @Test
    void historicoLimitaQuantidadeExibidaEInformaTotal() throws SQLException {
        for (int i = 0; i < DashboardService.LIMITE_HISTORICO + 5; i++) {
            movimentacoes.adicionar("A1", TipoMovimentacao.SAIDA, DirecaoMovimentacao.SAIDA, 1, 500 - i,
                    INICIO.plusMinutes(i));
        }
        List<ProdutoView> lista = painel.listarProdutos();

        HistoricoMovimentacoes historico = painel.consultarHistorico(lista, "A1", null, null, null);
        HistoricoMovimentacoes vazio = painel.consultarHistorico(lista, "B2", null, null, null);

        assertEquals(DashboardService.LIMITE_HISTORICO, historico.itens().size());
        assertEquals(DashboardService.LIMITE_HISTORICO + 5, historico.totalEncontrado());
        assertTrue(historico.truncado());
        assertTrue(vazio.itens().isEmpty());
        assertFalse(vazio.truncado());
    }

    @Test
    void filtraProdutosPorBuscaESomenteAlertas() throws SQLException {
        List<ProdutoView> lista = painel.listarProdutos();

        assertEquals(List.of("B2"), codigos(DashboardService.filtrarProdutos(lista, false, "broc")));
        assertEquals(List.of("C3"), codigos(DashboardService.filtrarProdutos(lista, false, "c3")));
        assertEquals(List.of("B2", "C3"), codigos(DashboardService.filtrarProdutos(lista, true, "")));
        assertEquals(List.of("C3"), codigos(DashboardService.filtrarProdutos(lista, true, "cola")));
        assertEquals(4, DashboardService.filtrarProdutos(lista, false, "  ").size());
    }

    @Test
    void calculaCoberturaDoMinimoParaABarraDeNivel() {
        assertEquals(30, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 3, 10)).nivelPercentual());
        assertEquals(75, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 7, 9)).nivelPercentual());
        assertEquals(0, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 0, 9)).nivelPercentual());
        assertEquals(100, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 50, 10)).nivelPercentual());
        assertEquals(100, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 5, 0)).nivelPercentual());
        assertEquals(7, ProdutoView.de(new Produto("X", "X", BigDecimal.ONE, 3, 10)).faltam());
    }

    @Test
    void descreveMovimentacoesSemRepetirMotivoEIdentificaEstornos() {
        MovimentacaoEstoque venda = new MovimentacaoEstoque(1, "A1", TipoMovimentacao.SAIDA,
                DirecaoMovimentacao.SAIDA, 2, 10, 8, null, MotivoMovimentacao.VENDA, INICIO, 2L, null, null);
        MovimentacaoEstoque estorno = new MovimentacaoEstoque(2, "A1", TipoMovimentacao.SAIDA,
                DirecaoMovimentacao.ENTRADA, 2, 8, 10, null, MotivoMovimentacao.ESTORNO, INICIO, null, 1L, null);
        MovimentacaoEstoque perda = new MovimentacaoEstoque(3, "A1", TipoMovimentacao.PERDA,
                DirecaoMovimentacao.SAIDA, 1, 10, 9, null, MotivoMovimentacao.PERDA, INICIO, null, null, null);

        assertEquals("Saída · Venda", MovimentacaoView.de(venda, "Arruela").descricao());
        assertEquals("−2", MovimentacaoView.de(venda, "Arruela").quantidadeComSinal());
        assertEquals("Estorno de saída", MovimentacaoView.de(estorno, "Arruela").descricao());
        assertEquals("+2", MovimentacaoView.de(estorno, "Arruela").quantidadeComSinal());
        assertEquals("Perda", MovimentacaoView.de(perda, "Arruela").descricao());
    }

    private List<String> codigos(List<ProdutoView> lista) {
        return lista.stream().map(ProdutoView::codigo).toList();
    }
}
