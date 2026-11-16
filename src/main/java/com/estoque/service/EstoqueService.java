package com.estoque.service;

import com.estoque.entities.Produto;
import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.ResumoEstoque;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.repository.MovimentacaoRepository;
import com.estoque.repository.MovimentacaoRepositoryJdbc;
import com.estoque.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoRepositoryJdbc relatorioRepository;

    public EstoqueService(ProdutoRepository produtoRepository) {
        this(produtoRepository, new MovimentacaoRepositoryJdbc());
    }

    public EstoqueService(
            ProdutoRepository produtoRepository,
            MovimentacaoRepository movimentacaoRepository
    ) {
        if (produtoRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de produtos e obrigatorio."
            );
        }

        if (movimentacaoRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de movimentacoes e obrigatorio."
            );
        }

        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.relatorioRepository = new MovimentacaoRepositoryJdbc();
    }

    public void cadastrar(Produto produto) throws SQLException {
        if (produtoRepository.buscarPorCodigo(produto.getCodigo()).isPresent()) {
            throw new IllegalArgumentException(
                    "Ja existe um produto com esse codigo."
            );
        }

        produtoRepository.inserir(produto);
    }

    public List<Produto> listar() throws SQLException {
        return produtoRepository.listar();
    }

    public Produto buscar(String codigo) throws SQLException {
        return produtoRepository.buscarPorCodigo(codigo)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Produto nao encontrado."
                        )
                );
    }

    public void atualizar(
            String codigo,
            String nome,
            BigDecimal preco
    ) throws SQLException {
        atualizar(codigo, nome, preco, buscar(codigo).getEstoqueMinimo());
    }

    public void atualizar(
            String codigo,
            String nome,
            BigDecimal preco,
            int estoqueMinimo
    ) throws SQLException {
        Produto produto = buscar(codigo);
        produto.atualizar(nome, preco);
        produto.atualizarEstoqueMinimo(estoqueMinimo);
        salvar(produto);
    }

    public void adicionarEstoque(String codigo, int quantidade)
            throws SQLException {
        registrarMovimentacao(
                codigo,
                TipoMovimentacao.ENTRADA,
                null,
                quantidade,
                MotivoMovimentacao.COMPRA,
                null,
                null
        );
    }

    public void retirarEstoque(String codigo, int quantidade)
            throws SQLException {
        registrarMovimentacao(
                codigo,
                TipoMovimentacao.SAIDA,
                null,
                quantidade,
                MotivoMovimentacao.VENDA,
                null,
                null
        );
    }

    public List<MovimentacaoEstoque> registrarMovimentacao(
            String codigo,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo,
            Long usuarioId,
            String codigoDestino
    ) throws SQLException {

        if (tipo == null) {
            throw new IllegalArgumentException(
                    "O tipo da movimentacao e obrigatorio."
            );
        }

        DirecaoMovimentacao direcaoEfetiva = switch (tipo) {
            case ENTRADA, DEVOLUCAO -> DirecaoMovimentacao.ENTRADA;
            case SAIDA, PERDA, TRANSFERENCIA -> DirecaoMovimentacao.SAIDA;
            case AJUSTE -> {
                if (direcao == null) {
                    throw new IllegalArgumentException(
                            "Informe se o ajuste aumenta ou reduz o estoque."
                    );
                }
                yield direcao;
            }
        };

        MotivoMovimentacao motivoEfetivo =
                motivo == null ? motivoPadrao(tipo) : motivo;

        return movimentacaoRepository.registrar(
                codigo,
                tipo,
                direcaoEfetiva,
                quantidade,
                motivoEfetivo,
                usuarioId,
                codigoDestino
        );
    }

    public List<MovimentacaoEstoque> estornarMovimentacao(
            long id,
            Long usuarioId
    ) throws SQLException {
        return movimentacaoRepository.estornar(id, usuarioId);
    }

    public List<MovimentacaoEstoque> listarHistorico(
            String codigoProduto,
            TipoMovimentacao tipo,
            LocalDate dataInicio,
            LocalDate dataFim
    ) throws SQLException {
        return movimentacaoRepository.listar(
                codigoProduto,
                tipo,
                dataInicio,
                dataFim
        );
    }

    public List<Produto> listarAlertasEstoqueMinimo() throws SQLException {
        List<Produto> alertas = new ArrayList<>();

        for (Produto produto : produtoRepository.listar()) {
            if (produto.getEstoqueMinimo() > 0
                    && produto.getQuantidade() < produto.getEstoqueMinimo()) {
                alertas.add(produto);
            }
        }

        return alertas;
    }

    public List<ResumoEstoque> relatorioEstoque() throws SQLException {
        return movimentacaoRepository.resumir();
    }

    public void remover(String codigo) throws SQLException {
        if (!produtoRepository.remover(codigo)) {
            throw new IllegalArgumentException(
                    "Produto nao encontrado."
            );
        }
    }

    private void salvar(Produto produto) throws SQLException {
        if (!produtoRepository.atualizar(produto)) {
            throw new IllegalArgumentException(
                    "Produto nao encontrado."
            );
        }
    }

    private MotivoMovimentacao motivoPadrao(TipoMovimentacao tipo) {
        return switch (tipo) {
            case ENTRADA -> MotivoMovimentacao.COMPRA;
            case SAIDA -> MotivoMovimentacao.VENDA;
            case AJUSTE -> MotivoMovimentacao.AJUSTE;
            case PERDA -> MotivoMovimentacao.PERDA;
            case DEVOLUCAO -> MotivoMovimentacao.DEVOLUCAO;
            case TRANSFERENCIA -> MotivoMovimentacao.TRANSFERENCIA;
        };
    }
}