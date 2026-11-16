package com.estoque.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/estoque?createDatabaseIfNotExist=true";
    private static final String CREATE_PRODUCTS_TABLE =
            "CREATE TABLE IF NOT EXISTS produtos ("
                    + "codigo VARCHAR(255) PRIMARY KEY NOT NULL,"
                    + "nome TEXT NOT NULL,"
                    + "preco DECIMAL(65, 30) NOT NULL CHECK (preco >= 0),"
                    + "quantidade INTEGER NOT NULL CHECK (quantidade >= 0),"
                    + "estoque_minimo INTEGER NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0)"
                    + ")";
    private static final String CREATE_SUPPLIERS_TABLE =
            "CREATE TABLE IF NOT EXISTS fornecedores ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "nome VARCHAR(255) NOT NULL,"
                    + "cnpj VARCHAR(18) UNIQUE,"
                    + "telefone VARCHAR(20),"
                    + "email VARCHAR(255),"
                    + "endereco TEXT"
                    + ")";
    private static final String CREATE_USERS_TABLE =
            "CREATE TABLE IF NOT EXISTS usuarios ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "nome VARCHAR(255) NOT NULL,"
                    + "email VARCHAR(255) NOT NULL UNIQUE,"
                    + "senha_hash VARCHAR(255) NOT NULL,"
                    + "perfil VARCHAR(20) NOT NULL"
                    + ")";
                private static final String CREATE_MOVEMENTS_TABLE =
                    "CREATE TABLE IF NOT EXISTS movimentacoes_estoque ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "codigo_produto VARCHAR(255) NOT NULL,"
                        + "tipo VARCHAR(20) NOT NULL,"
                        + "direcao VARCHAR(10) NOT NULL,"
                        + "quantidade INTEGER NOT NULL CHECK (quantidade > 0),"
                        + "saldo_antes INTEGER NOT NULL CHECK (saldo_antes >= 0),"
                        + "saldo_depois INTEGER NOT NULL CHECK (saldo_depois >= 0),"
                        + "usuario_id BIGINT NULL,"
                        + "motivo VARCHAR(30) NOT NULL,"
                        + "data_movimentacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "estornada_por_id BIGINT NULL,"
                        + "estorno_de_id BIGINT NULL UNIQUE,"
                        + "grupo_id VARCHAR(36) NULL,"
                        + "codigo_destino VARCHAR(255) NULL,"
                        + "INDEX idx_mov_produto_data (codigo_produto, data_movimentacao),"
                        + "INDEX idx_mov_tipo_data (tipo, data_movimentacao),"
                        + "CONSTRAINT fk_mov_produto FOREIGN KEY (codigo_produto) "
                        + "REFERENCES produtos(codigo) ON DELETE RESTRICT,"
                        + "CONSTRAINT fk_mov_usuario FOREIGN KEY (usuario_id) "
                        + "REFERENCES usuarios(id) ON DELETE SET NULL"
                        + ")";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = environmentOrDefault("DB_URL", DEFAULT_URL);
        String user = environmentOrDefault("DB_USER", null);
        if (user == null) {
            throw new SQLException("Defina a variavel de ambiente DB_USER para conectar ao MySQL.");
        }
        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = "";
        }
        return DriverManager.getConnection(url, user, password);
    }

    public static void initialize() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_PRODUCTS_TABLE);
            statement.execute(CREATE_SUPPLIERS_TABLE);
            statement.execute(CREATE_USERS_TABLE);
            garantirColunaEstoqueMinimo(connection, statement);
            statement.execute(CREATE_MOVEMENTS_TABLE);
        }
    }

    private static void garantirColunaEstoqueMinimo(Connection connection, Statement statement)
            throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, "produtos", "estoque_minimo")) {
            if (!columns.next()) {
                statement.execute("ALTER TABLE produtos ADD COLUMN estoque_minimo "
                        + "INTEGER NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0)");
            }
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
