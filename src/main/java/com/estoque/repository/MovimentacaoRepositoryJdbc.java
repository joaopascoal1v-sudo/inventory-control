package com.estoque.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.ResumoEstoque;
import com.estoque.entities.TipoMovimentacao;

public class MovimentacaoRepositoryJdbc implements MovimentacaoRepository {
    private static final String PRODUCT_FOR_UPDATE =
            "SELECT quantidade FROM produtos WHERE codigo = ? FOR UPDATE";
    private static final String UPDATE_STOCK = "UPDATE produtos SET quantidade = ? WHERE codigo = ?";
    private static final String INSERT_MOVEMENT =
            "INSERT INTO movimentacoes_estoque (codigo_produto, tipo, direcao, quantidade, "
                    + "saldo_antes, saldo_depois, usuario_id, motivo, data_movimentacao, "
                    + "estorno_de_id, grupo_id, codigo_destino) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String HISTORY_SELECT =
            "SELECT id, codigo_produto, tipo, direcao, quantidade, saldo_antes, saldo_depois, "
                    + "usuario_id, motivo, data_movimentacao, estornada_por_id, estorno_de_id, "
                    + "codigo_destino, grupo_id FROM movimentacoes_estoque";

    private final ConnectionProvider connectionProvider;

    public MovimentacaoRepositoryJdbc() {
        this(DatabaseConnection::getConnection);
    }

    public MovimentacaoRepositoryJdbc(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("O provedor de conexoes e obrigatorio.");
        }
        this.connectionProvider = connectionProvider;
    }

    @Override
    public List<MovimentacaoEstoque> registrar(
            String codigoProduto,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo,
            Long usuarioId,
            String codigoDestino) throws SQLException {
        validar(codigoProduto, tipo, direcao, quantidade, motivo);
        boolean transferencia = tipo == TipoMovimentacao.TRANSFERENCIA;
        String destino;
        if (transferencia) {
            if (codigoDestino == null || codigoDestino.isBlank()
                    || codigoProduto.equals(codigoDestino.trim())) {
                throw new IllegalArgumentException("Informe um produto de destino diferente da origem.");
            }
            destino = codigoDestino.trim();
        } else {
            if (codigoDestino != null && !codigoDestino.isBlank()) {
                throw new IllegalArgumentException("Produto de destino so e permitido em transferencias.");
            }
            destino = null;
        }

        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validarUsuario(connection, usuarioId);
                List<String> codigos = new ArrayList<>();
                codigos.add(codigoProduto);
                if (transferencia) {
                    codigos.add(destino);
                }
                codigos.sort(Comparator.naturalOrder());
                Map<String, Integer> saldos = new HashMap<>();
                for (String codigo : codigos) {
                    saldos.put(codigo, bloquearProduto(connection, codigo));
                }

                String grupoId = transferencia ? UUID.randomUUID().toString() : null;
                List<MovimentacaoEstoque> criadas = new ArrayList<>();
                DirecaoMovimentacao direcaoOrigem = transferencia
                        ? DirecaoMovimentacao.SAIDA : direcao;
                criadas.add(aplicarMovimento(connection, codigoProduto, tipo, direcaoOrigem,
                        quantidade, motivo, usuarioId, saldos, grupoId, destino, null));
                if (transferencia) {
                    criadas.add(aplicarMovimento(connection, destino, tipo,
                            DirecaoMovimentacao.ENTRADA, quantidade, motivo, usuarioId,
                            saldos, grupoId, codigoProduto, null));
                }
                connection.commit();
                return criadas;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    @Override
    public List<MovimentacaoEstoque> estornar(long id, Long usuarioId) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Identificador de movimentacao invalido.");
        }
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validarUsuario(connection, usuarioId);
                MovimentacaoEstoque original = buscarPorId(connection, id, true);
                List<MovimentacaoEstoque> originais = buscarGrupoEstornavel(connection, original);
                if (originais.stream().anyMatch(item -> item.estornadaPorId() != null)) {
                    throw new IllegalArgumentException("Esta movimentacao ja foi estornada.");
                }

                List<String> codigos = originais.stream()
                        .map(MovimentacaoEstoque::codigoProduto).distinct().sorted().toList();
                Map<String, Integer> saldos = new HashMap<>();
                for (String codigo : codigos) {
                    saldos.put(codigo, bloquearProduto(connection, codigo));
                }

                String novoGrupo = originais.size() > 1 ? UUID.randomUUID().toString() : null;
                List<MovimentacaoEstoque> estornos = new ArrayList<>();
                for (MovimentacaoEstoque movimento : originais) {
                    DirecaoMovimentacao contraria = movimento.direcao() == DirecaoMovimentacao.ENTRADA
                            ? DirecaoMovimentacao.SAIDA : DirecaoMovimentacao.ENTRADA;
                    estornos.add(aplicarMovimento(connection, movimento.codigoProduto(), movimento.tipo(),
                            contraria, movimento.quantidade(), MotivoMovimentacao.ESTORNO,
                            usuarioId, saldos, novoGrupo, movimento.codigoDestino(), movimento.id()));
                }
                for (int indice = 0; indice < originais.size(); indice++) {
                    marcarEstornada(connection, originais.get(indice).id(), estornos.get(indice).id());
                }
                connection.commit();
                return estornos;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    @Override
    public List<MovimentacaoEstoque> listar(
            String codigoProduto,
            TipoMovimentacao tipo,
            LocalDate dataInicio,
            LocalDate dataFim) throws SQLException {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial nao pode ser posterior a data final.");
        }
        StringBuilder sql = new StringBuilder(HISTORY_SELECT);
        List<Object> parametros = new ArrayList<>();
        List<String> filtros = new ArrayList<>();
        if (codigoProduto != null && !codigoProduto.isBlank()) {
            filtros.add("codigo_produto = ?");
            parametros.add(codigoProduto.trim());
        }
        if (tipo != null) {
            filtros.add("tipo = ?");
            parametros.add(tipo.name());
        }
        if (dataInicio != null) {
            filtros.add("data_movimentacao >= ?");
            parametros.add(Timestamp.valueOf(dataInicio.atStartOfDay()));
        }
        if (dataFim != null) {
            filtros.add("data_movimentacao < ?");
            parametros.add(Timestamp.valueOf(dataFim.plusDays(1).atStartOfDay()));
        }
        if (!filtros.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", filtros));
        }
        sql.append(" ORDER BY data_movimentacao DESC, id DESC");

        List<MovimentacaoEstoque> movimentos = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int indice = 0; indice < parametros.size(); indice++) {
                statement.setObject(indice + 1, parametros.get(indice));
            }
            try (ResultSet resultado = statement.executeQuery()) {
                while (resultado.next()) {
                    movimentos.add(mapear(resultado));
                }
            }
        }
        return movimentos;
    }

    @Override
    public List<ResumoEstoque> resumir() throws SQLException {
        String sql = "SELECT p.codigo, p.nome, p.quantidade, "
                + "COALESCE(SUM(CASE WHEN m.direcao = 'ENTRADA' THEN m.quantidade ELSE 0 END), 0) entradas, "
                + "COALESCE(SUM(CASE WHEN m.direcao = 'SAIDA' THEN m.quantidade ELSE 0 END), 0) saidas "
                + "FROM produtos p LEFT JOIN movimentacoes_estoque m ON m.codigo_produto = p.codigo "
                + "GROUP BY p.codigo, p.nome, p.quantidade ORDER BY p.codigo";
        List<ResumoEstoque> resumo = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultado = statement.executeQuery()) {
            while (resultado.next()) {
                resumo.add(new ResumoEstoque(resultado.getString("codigo"), resultado.getString("nome"),
                        resultado.getLong("entradas"), resultado.getLong("saidas"),
                        resultado.getInt("quantidade")));
            }
        }
        return resumo;
    }

    private MovimentacaoEstoque aplicarMovimento(
            Connection connection,
            String codigo,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo,
            Long usuarioId,
            Map<String, Integer> saldos,
            String grupoId,
            String codigoDestino,
            Long estornoDeId) throws SQLException {
        int saldoAntes = saldos.get(codigo);
        int saldoDepois;
        try {
            saldoDepois = direcao == DirecaoMovimentacao.ENTRADA
                    ? Math.addExact(saldoAntes, quantidade) : Math.subtractExact(saldoAntes, quantidade);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Quantidade fora do limite permitido.", e);
        }
        if (saldoDepois < 0) {
            throw new IllegalArgumentException("Estoque insuficiente.");
        }
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_STOCK)) {
            statement.setInt(1, saldoDepois);
            statement.setString(2, codigo);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Produto nao encontrado: " + codigo);
            }
        }
        saldos.put(codigo, saldoDepois);
        LocalDateTime data = LocalDateTime.now();
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_MOVEMENT, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, codigo);
            statement.setString(2, tipo.name());
            statement.setString(3, direcao.name());
            statement.setInt(4, quantidade);
            statement.setInt(5, saldoAntes);
            statement.setInt(6, saldoDepois);
            if (usuarioId == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, usuarioId);
            }
            statement.setString(8, motivo.name());
            statement.setTimestamp(9, Timestamp.valueOf(data));
            if (estornoDeId == null) {
                statement.setNull(10, java.sql.Types.BIGINT);
            } else {
                statement.setLong(10, estornoDeId);
            }
            statement.setString(11, grupoId);
            statement.setString(12, codigoDestino);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("O banco nao retornou o identificador da movimentacao.");
                }
                return new MovimentacaoEstoque(keys.getLong(1), codigo, tipo, direcao, quantidade,
                        saldoAntes, saldoDepois, usuarioId, motivo, data, null, estornoDeId, codigoDestino);
            }
        }
    }

    private int bloquearProduto(Connection connection, String codigo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PRODUCT_FOR_UPDATE)) {
            statement.setString(1, codigo);
            try (ResultSet resultado = statement.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalArgumentException("Produto nao encontrado: " + codigo);
                }
                return resultado.getInt("quantidade");
            }
        }
    }

    private void validarUsuario(Connection connection, Long usuarioId) throws SQLException {
        if (usuarioId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM usuarios WHERE id = ?")) {
            statement.setLong(1, usuarioId);
            try (ResultSet resultado = statement.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalArgumentException("Usuario responsavel nao encontrado.");
                }
            }
        }
    }

    private MovimentacaoEstoque buscarPorId(Connection connection, long id, boolean bloquear) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                HISTORY_SELECT + " WHERE id = ?" + (bloquear ? " FOR UPDATE" : ""))) {
            statement.setLong(1, id);
            try (ResultSet resultado = statement.executeQuery()) {
                if (!resultado.next()) {
                    throw new IllegalArgumentException("Movimentacao nao encontrada.");
                }
                return mapear(resultado);
            }
        }
    }

    private List<MovimentacaoEstoque> buscarGrupoEstornavel(
            Connection connection, MovimentacaoEstoque original) throws SQLException {
        String sql = HISTORY_SELECT + (original.codigoDestino() != null
                ? " WHERE grupo_id = (SELECT grupo_id FROM movimentacoes_estoque WHERE id = ?) "
                    + "AND estorno_de_id IS NULL ORDER BY id FOR UPDATE"
                : " WHERE id = ? FOR UPDATE");
        List<MovimentacaoEstoque> movimentos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, original.id());
            try (ResultSet resultado = statement.executeQuery()) {
                while (resultado.next()) {
                    movimentos.add(mapear(resultado));
                }
            }
        }
        if (movimentos.isEmpty()) {
            throw new IllegalArgumentException("Movimentacao nao encontrada.");
        }
        return movimentos;
    }

    private void marcarEstornada(Connection connection, long originalId, long estornoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE movimentacoes_estoque SET estornada_por_id = ? "
                        + "WHERE id = ? AND estornada_por_id IS NULL")) {
            statement.setLong(1, estornoId);
            statement.setLong(2, originalId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Esta movimentacao ja foi estornada.");
            }
        }
    }

    private MovimentacaoEstoque mapear(ResultSet resultado) throws SQLException {
        long usuarioId = resultado.getLong("usuario_id");
        Long usuario = resultado.wasNull() ? null : usuarioId;
        long estornadaPorId = resultado.getLong("estornada_por_id");
        Long estornadaPor = resultado.wasNull() ? null : estornadaPorId;
        long estornoDeId = resultado.getLong("estorno_de_id");
        Long estornoDe = resultado.wasNull() ? null : estornoDeId;
        return new MovimentacaoEstoque(
                resultado.getLong("id"), resultado.getString("codigo_produto"),
                TipoMovimentacao.valueOf(resultado.getString("tipo")),
                DirecaoMovimentacao.valueOf(resultado.getString("direcao")),
                resultado.getInt("quantidade"), resultado.getInt("saldo_antes"),
                resultado.getInt("saldo_depois"), usuario,
                MotivoMovimentacao.valueOf(resultado.getString("motivo")),
                resultado.getTimestamp("data_movimentacao").toLocalDateTime(),
                estornadaPor, estornoDe, resultado.getString("codigo_destino"));
    }

    private void validar(
            String codigoProduto,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade,
            MotivoMovimentacao motivo) {
        if (codigoProduto == null || codigoProduto.isBlank() || tipo == null || motivo == null) {
            throw new IllegalArgumentException("Produto, tipo e motivo sao obrigatorios.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        if (direcao == null) {
            throw new IllegalArgumentException("A direcao da movimentacao e obrigatoria.");
        }
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }
}