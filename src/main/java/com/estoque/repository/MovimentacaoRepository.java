package com.estoque.repository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.ResumoEstoque;
import com.estoque.entities.TipoMovimentacao;

public interface MovimentacaoRepository {
    List<MovimentacaoEstoque> registrar(
            String codigoProduto,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo,
            Long usuarioId,
            String codigoDestino) throws SQLException;

    List<MovimentacaoEstoque> estornar(long id, Long usuarioId) throws SQLException;

    List<MovimentacaoEstoque> listar(
            String codigoProduto,
            TipoMovimentacao tipo,
            LocalDate dataInicio,
            LocalDate dataFim) throws SQLException;

    /** Retorna as movimentacoes mais recentes, da mais nova para a mais antiga. */
    List<MovimentacaoEstoque> listarUltimas(int limite) throws SQLException;

    List<ResumoEstoque> resumir() throws SQLException;
}