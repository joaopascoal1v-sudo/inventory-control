package com.estoque.service;

import com.estoque.entities.Produto;
import com.estoque.repository.ProdutoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class EstoqueService {
    private final ProdutoRepository produtoRepository;

    public EstoqueService(ProdutoRepository produtoRepository) {
        if (produtoRepository == null) {
            throw new IllegalArgumentException("O repositorio de produtos e obrigatorio.");
        }
        this.produtoRepository = produtoRepository;
    }

    public void cadastrar(Produto produto) throws SQLException {
        if (produtoRepository.buscarPorCodigo(produto.getCodigo()).isPresent()) {
            throw new IllegalArgumentException("Ja existe um produto com esse codigo.");
        }
        produtoRepository.inserir(produto);
    }

    public List<Produto> listar() throws SQLException {
        return produtoRepository.listar();
    }

    public Produto buscar(String codigo) throws SQLException {
        return produtoRepository.buscarPorCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado."));
    }

    public void atualizar(String codigo, String nome, BigDecimal preco) throws SQLException {
        Produto produto = buscar(codigo);
        produto.atualizar(nome, preco);
        salvar(produto);
    }

    public void adicionarEstoque(String codigo, int quantidade) throws SQLException {
        Produto produto = buscar(codigo);
        produto.adicionarEstoque(quantidade);
        salvar(produto);
    }

    public void retirarEstoque(String codigo, int quantidade) throws SQLException {
        Produto produto = buscar(codigo);
        produto.retirarEstoque(quantidade);
        salvar(produto);
    }

    public void remover(String codigo) throws SQLException {
        if (!produtoRepository.remover(codigo)) {
            throw new IllegalArgumentException("Produto nao encontrado.");
        }
    }

    private void salvar(Produto produto) throws SQLException {
        if (!produtoRepository.atualizar(produto)) {
            throw new IllegalArgumentException("Produto nao encontrado.");
        }
    }
}
