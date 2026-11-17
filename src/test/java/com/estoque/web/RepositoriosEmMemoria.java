package com.estoque.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.Produto;
import com.estoque.entities.ResumoEstoque;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.entities.Usuario;
import com.estoque.repository.MovimentacaoRepository;
import com.estoque.repository.ProdutoRepository;
import com.estoque.repository.UsuarioRepository;

/** Implementacoes em memoria dos repositorios, usadas pelos testes das telas web. */
final class RepositoriosEmMemoria {

    private RepositoriosEmMemoria() {
    }

    static final class Produtos implements ProdutoRepository {
        private final Map<String, Produto> produtos = new TreeMap<>();

        @Override
        public void inserir(Produto produto) {
            produtos.put(produto.getCodigo(), produto);
        }

        @Override
        public List<Produto> listar() {
            return new ArrayList<>(produtos.values());
        }

        @Override
        public Optional<Produto> buscarPorCodigo(String codigo) {
            return Optional.ofNullable(produtos.get(codigo));
        }

        @Override
        public boolean atualizar(Produto produto) {
            return produtos.replace(produto.getCodigo(), produto) != null;
        }

        @Override
        public boolean remover(String codigo) {
            return produtos.remove(codigo) != null;
        }
    }

    static final class Movimentacoes implements MovimentacaoRepository {
        private static final Comparator<MovimentacaoEstoque> MAIS_RECENTES_PRIMEIRO =
                Comparator.comparing(MovimentacaoEstoque::data)
                        .thenComparingLong(MovimentacaoEstoque::id)
                        .reversed();

        private final List<MovimentacaoEstoque> movimentos = new ArrayList<>();

        MovimentacaoEstoque adicionar(
                String codigo, TipoMovimentacao tipo, DirecaoMovimentacao direcao,
                int quantidade, int saldoAntes, LocalDateTime data) {
            int saldoDepois = direcao == DirecaoMovimentacao.ENTRADA
                    ? saldoAntes + quantidade
                    : saldoAntes - quantidade;
            MovimentacaoEstoque movimento = new MovimentacaoEstoque(
                    movimentos.size() + 1L, codigo, tipo, direcao, quantidade, saldoAntes, saldoDepois,
                    null, MotivoMovimentacao.OUTRO, data, null, null, null);
            movimentos.add(movimento);
            return movimento;
        }

        @Override
        public List<MovimentacaoEstoque> registrar(
                String codigoProduto, TipoMovimentacao tipo, DirecaoMovimentacao direcao, int quantidade,
                MotivoMovimentacao motivo, Long usuarioId, String codigoDestino) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MovimentacaoEstoque> estornar(long id, Long usuarioId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MovimentacaoEstoque> listar(
                String codigoProduto, TipoMovimentacao tipo, LocalDate dataInicio, LocalDate dataFim) {
            return movimentos.stream()
                    .filter(m -> codigoProduto == null || codigoProduto.isBlank()
                            || m.codigoProduto().equals(codigoProduto))
                    .filter(m -> tipo == null || m.tipo() == tipo)
                    .filter(m -> dataInicio == null || !m.data().toLocalDate().isBefore(dataInicio))
                    .filter(m -> dataFim == null || !m.data().toLocalDate().isAfter(dataFim))
                    .sorted(MAIS_RECENTES_PRIMEIRO)
                    .toList();
        }

        @Override
        public List<MovimentacaoEstoque> listarUltimas(int limite) {
            return movimentos.stream().sorted(MAIS_RECENTES_PRIMEIRO).limit(limite).toList();
        }

        @Override
        public List<ResumoEstoque> resumir() {
            return List.of();
        }
    }

    static final class Usuarios implements UsuarioRepository {
        private final List<Usuario> usuarios = new ArrayList<>();

        @Override
        public boolean existeAlgum() {
            return !usuarios.isEmpty();
        }

        @Override
        public void inserir(Usuario usuario, String senhaHash) {
            usuarios.add(new Usuario(usuarios.size() + 1L, usuario.getNome(), usuario.getEmail(),
                    senhaHash, usuario.getPerfil()));
        }

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            return usuarios.stream().filter(u -> u.getEmail().equals(email)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorId(long id) {
            return usuarios.stream().filter(u -> u.getId() == id).findFirst();
        }
    }
}
