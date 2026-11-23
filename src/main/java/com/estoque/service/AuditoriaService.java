package com.estoque.service;

import com.estoque.database.DatabaseConnection;
import com.estoque.entities.LogAuditoria;
import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class AuditoriaService {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS logs_auditoria ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "usuario_nome VARCHAR(255) NOT NULL,"
                    + "usuario_email VARCHAR(255) NOT NULL,"
                    + "usuario_perfil VARCHAR(40) NOT NULL,"
                    + "categoria VARCHAR(30) NOT NULL,"
                    + "acao VARCHAR(80) NOT NULL,"
                    + "detalhes TEXT NOT NULL,"
                    + "origem VARCHAR(120) NOT NULL"
                    + ")";

    private static final String INSERT_SQL =
            "INSERT INTO logs_auditoria "
                    + "(data_hora, usuario_nome, usuario_email, usuario_perfil, categoria, acao, detalhes, origem) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_ALL_SQL =
            "SELECT id, data_hora, usuario_nome, usuario_email, usuario_perfil, categoria, acao, detalhes, origem "
                    + "FROM logs_auditoria ORDER BY data_hora DESC, id DESC LIMIT ?";

    private static final String SELECT_BY_CATEGORY_SQL =
            "SELECT id, data_hora, usuario_nome, usuario_email, usuario_perfil, categoria, acao, detalhes, origem "
                    + "FROM logs_auditoria WHERE UPPER(categoria) = UPPER(?) ORDER BY data_hora DESC, id DESC LIMIT ?";

    private final List<LogAuditoria> fallbackMemoria = new CopyOnWriteArrayList<>();
    private final AtomicLong geradorIdMemoria = new AtomicLong(1);

    public void registrarAcesso(Usuario usuario, String acao, String detalhes, String origem) {
        registrar(
                usuario != null ? usuario.getNome() : "Visitante",
                usuario != null ? usuario.getEmail() : "-",
                rotuloPerfil(usuario != null ? usuario.getPerfil() : null),
                "ACESSO",
                acao,
                detalhes,
                origem);
    }

    public void registrarModificacao(Usuario usuario, String acao, String detalhes, String origem) {
        registrar(
                usuario != null ? usuario.getNome() : "Sistema",
                usuario != null ? usuario.getEmail() : "-",
                rotuloPerfil(usuario != null ? usuario.getPerfil() : null),
                "MODIFICACAO",
                acao,
                detalhes,
                origem);
    }

    public void registrar(
            String usuarioNome,
            String usuarioEmail,
            String usuarioPerfil,
            String categoria,
            String acao,
            String detalhes,
            String origem
    ) {
        LocalDateTime agora = LocalDateTime.now();
        String nomeLimpo = valorOuPadrao(usuarioNome, "Usuário");
        String emailLimpo = valorOuPadrao(usuarioEmail, "-");
        String perfilLimpo = valorOuPadrao(usuarioPerfil, "Funcionário");
        String categoriaLimpa = valorOuPadrao(categoria, "MODIFICACAO").toUpperCase();
        String acaoLimpa = valorOuPadrao(acao, "Ação");
        String detalhesLimpos = valorOuPadrao(detalhes, "-");
        String origemLimpa = valorOuPadrao(origem, "Local");

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute(CREATE_TABLE_SQL);
            }
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setTimestamp(1, Timestamp.valueOf(agora));
                ps.setString(2, nomeLimpo);
                ps.setString(3, emailLimpo);
                ps.setString(4, perfilLimpo);
                ps.setString(5, categoriaLimpa);
                ps.setString(6, acaoLimpa);
                ps.setString(7, detalhesLimpos);
                ps.setString(8, origemLimpa);
                ps.executeUpdate();
                return;
            }
        } catch (SQLException ignored) {
            // Fallback em memoria caso o banco nao esteja inicializado em testes isolados
        }

        fallbackMemoria.add(0, new LogAuditoria(
                geradorIdMemoria.getAndIncrement(),
                agora,
                nomeLimpo,
                emailLimpo,
                perfilLimpo,
                categoriaLimpa,
                acaoLimpa,
                detalhesLimpos,
                origemLimpa));
    }

    public List<LogAuditoria> listar(String filtroCategoria, int limite) {
        int max = Math.max(1, Math.min(limite, 500));
        boolean filtrar = filtroCategoria != null
                && ("ACESSO".equalsIgnoreCase(filtroCategoria) || "MODIFICACAO".equalsIgnoreCase(filtroCategoria));

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute(CREATE_TABLE_SQL);
            }
            String sql = filtrar ? SELECT_BY_CATEGORY_SQL : SELECT_ALL_SQL;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (filtrar) {
                    ps.setString(1, filtroCategoria.trim().toUpperCase());
                    ps.setInt(2, max);
                } else {
                    ps.setInt(1, max);
                }
                List<LogAuditoria> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ts = rs.getTimestamp("data_hora");
                        lista.add(new LogAuditoria(
                                rs.getLong("id"),
                                ts != null ? ts.toLocalDateTime() : LocalDateTime.now(),
                                rs.getString("usuario_nome"),
                                rs.getString("usuario_email"),
                                rs.getString("usuario_perfil"),
                                rs.getString("categoria"),
                                rs.getString("acao"),
                                rs.getString("detalhes"),
                                rs.getString("origem")));
                    }
                }
                if (!lista.isEmpty() || fallbackMemoria.isEmpty()) {
                    return lista;
                }
            }
        } catch (SQLException ignored) {
        }

        return fallbackMemoria.stream()
                .filter(log -> !filtrar || log.categoria().equalsIgnoreCase(filtroCategoria))
                .limit(max)
                .toList();
    }

    private static String rotuloPerfil(PerfilAcesso perfil) {
        if (perfil == null) {
            return "Visitante";
        }
        return perfil == PerfilAcesso.ADMIN ? "Administrador" : "Funcionário";
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return (valor == null || valor.isBlank()) ? padrao : valor.trim();
    }
}
