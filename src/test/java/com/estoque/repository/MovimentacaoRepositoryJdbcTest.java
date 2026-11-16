package com.estoque.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.estoque.entities.DirecaoMovimentacao;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
import com.estoque.entities.TipoMovimentacao;

class MovimentacaoRepositoryJdbcTest {
    private JdbcDataSource dataSource;
    private MovimentacaoRepositoryJdbc repository;

    @BeforeEach
    void prepararBanco() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        repository = new MovimentacaoRepositoryJdbc(this::abrirConexao);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE produtos (codigo VARCHAR(255) PRIMARY KEY, nome VARCHAR(255), "
                    + "quantidade INTEGER NOT NULL)");
            statement.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE movimentacoes_estoque ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, codigo_produto VARCHAR(255) NOT NULL, "
                    + "tipo VARCHAR(20) NOT NULL, direcao VARCHAR(10) NOT NULL, quantidade INTEGER NOT NULL, "
                    + "saldo_antes INTEGER NOT NULL, saldo_depois INTEGER NOT NULL, usuario_id BIGINT NULL, "
                    + "motivo VARCHAR(30) NOT NULL, data_movimentacao TIMESTAMP NOT NULL, "
                    + "estornada_por_id BIGINT NULL, estorno_de_id BIGINT NULL UNIQUE, "
                    + "grupo_id VARCHAR(36) NULL, codigo_destino VARCHAR(255) NULL)");
            statement.execute("INSERT INTO produtos (codigo, nome, quantidade) VALUES ('P1', 'Produto 1', 10)");
            statement.execute("INSERT INTO produtos (codigo, nome, quantidade) VALUES ('P2', 'Produto 2', 7)");
        }
    }

    @Test
    void registraEntradaSaidaERejeitaSaidaSemSaldoSemAlterarDados() throws SQLException {
        MovimentacaoEstoque entrada = registrar("P1", TipoMovimentacao.ENTRADA,
                DirecaoMovimentacao.ENTRADA, 5);
        assertEquals(10, entrada.saldoAntes());
        assertEquals(15, entrada.saldoDepois());
        assertEquals(15, saldo("P1"));

        MovimentacaoEstoque saida = registrar("P1", TipoMovimentacao.SAIDA,
                DirecaoMovimentacao.SAIDA, 3);
        assertEquals(12, saida.saldoDepois());
        assertEquals(12, saldo("P1"));

        assertThrows(IllegalArgumentException.class,
                () -> registrar("P1", TipoMovimentacao.SAIDA, DirecaoMovimentacao.SAIDA, 13));
        assertEquals(12, saldo("P1"));
        assertEquals(2, repository.listar("P1", null, null, null).size());
    }

    @Test
    void estornoCriaNovoRegistroMantemOriginalEImpedeDuplicacao() throws SQLException {
        MovimentacaoEstoque original = registrar("P1", TipoMovimentacao.ENTRADA,
                DirecaoMovimentacao.ENTRADA, 5);

        List<MovimentacaoEstoque> estornos = repository.estornar(original.id(), null);

        assertEquals(10, saldo("P1"));
        assertEquals(2, repository.listar("P1", null, null, null).size());
        assertEquals(original.id(), estornos.getFirst().estornoDeId());
        assertNotNull(repository.listar("P1", null, null, null).getLast().estornadaPorId());
        assertThrows(IllegalArgumentException.class, () -> repository.estornar(original.id(), null));
        assertEquals(2, repository.listar("P1", null, null, null).size());
    }

    @Test
    void filtraHistoricoPorProdutoERejeitaQuantidadeNaoPositiva() throws SQLException {
        registrar("P1", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 2);
        registrar("P2", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 4);

        List<MovimentacaoEstoque> filtradas = repository.listar("P1", null, null, LocalDate.now());

        assertEquals(1, filtradas.size());
        assertTrue(filtradas.stream().allMatch(item -> item.codigoProduto().equals("P1")));
        assertThrows(IllegalArgumentException.class,
                () -> registrar("P1", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 0));
        assertThrows(IllegalArgumentException.class,
                () -> registrar("P1", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, -1));
        assertEquals(12, saldo("P1"));
    }

    @Test
    void filtraHistoricoPorTipoEPeriodoOrdenandoMaisRecentesPrimeiro() throws SQLException {
        registrar("P1", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 2);
        registrar("P1", TipoMovimentacao.SAIDA, DirecaoMovimentacao.SAIDA, 1);
        registrar("P2", TipoMovimentacao.ENTRADA, DirecaoMovimentacao.ENTRADA, 4);

        LocalDate hoje = LocalDate.now();
        List<MovimentacaoEstoque> historico = repository.listar(null, null, hoje, hoje);
        List<MovimentacaoEstoque> saidas = repository.listar(null, TipoMovimentacao.SAIDA, hoje, hoje);

        assertEquals(3, historico.size());
        assertTrue(historico.get(0).id() > historico.get(1).id());
        assertTrue(historico.get(1).id() > historico.get(2).id());
        assertEquals(1, saidas.size());
        assertEquals(TipoMovimentacao.SAIDA, saidas.getFirst().tipo());
    }

    @Test
    void rejeitaDirecaoIncompativelComTipo() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.registrar("P1", TipoMovimentacao.ENTRADA,
                        DirecaoMovimentacao.SAIDA, 1, MotivoMovimentacao.COMPRA, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.registrar("P1", TipoMovimentacao.SAIDA,
                        DirecaoMovimentacao.ENTRADA, 1, MotivoMovimentacao.VENDA, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.registrar("P1", TipoMovimentacao.TRANSFERENCIA,
                        DirecaoMovimentacao.ENTRADA, 1, MotivoMovimentacao.TRANSFERENCIA, null, "P2"));
    }

    private MovimentacaoEstoque registrar(
            String codigo,
            TipoMovimentacao tipo,
            DirecaoMovimentacao direcao,
            int quantidade) throws SQLException {
        return repository.registrar(codigo, tipo, direcao, quantidade,
                MotivoMovimentacao.OUTRO, null, null).getFirst();
    }

    private int saldo(String codigo) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT quantidade FROM produtos WHERE codigo = ?")) {
            statement.setString(1, codigo);
            try (var resultado = statement.executeQuery()) {
                resultado.next();
                return resultado.getInt(1);
            }
        }
    }

    private Connection abrirConexao() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}