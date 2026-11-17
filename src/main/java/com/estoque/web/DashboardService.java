package com.estoque.web;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.service.EstoqueService;

/** Monta os dados das telas web a partir das regras de {@link EstoqueService}. */
public class DashboardService {

    public static final int LIMITE_ULTIMAS_MOVIMENTACOES = 10;
    public static final int LIMITE_HISTORICO = 200;

    private final EstoqueService estoque;

    public DashboardService(EstoqueService estoque) {
        if (estoque == null) {
            throw new IllegalArgumentException("O servico de estoque e obrigatorio.");
        }
        this.estoque = estoque;
    }

    public List<ProdutoView> listarProdutos() throws SQLException {
        return estoque.listar().stream().map(ProdutoView::de).toList();
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
