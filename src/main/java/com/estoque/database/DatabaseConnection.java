package com.estoque.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {

    private static final String DEFAULT_MYSQL_URL =
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

    private static final String CREATE_MOVIMENTACOES_TABLE =
            "CREATE TABLE IF NOT EXISTS movimentacoes ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "codigo_produto VARCHAR(255) NOT NULL,"
                    + "tipo VARCHAR(10) NOT NULL,"
                    + "quantidade INTEGER NOT NULL,"
                    + "data_movimentacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (codigo_produto) REFERENCES produtos(codigo)"
                    + ")";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = resolveUrl();
        if (url.startsWith("jdbc:h2:")) {
            String user = environmentOrDefault("DB_USER", "sa");
            String password = environmentOrDefault("DB_PASSWORD", "");
            return DriverManager.getConnection(url, user, password);
        }

        String user = environmentOrDefault("DB_USER", null);
        if (user == null) {
            throw new SQLException(
                    "Defina a variavel de ambiente DB_USER para conectar ao MySQL."
            );
        }

        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = "";
        }

        return DriverManager.getConnection(url, user, password);
    }

    private static String resolveUrl() {
        String envUrl = environmentOrDefault("DB_URL", null);
        if (envUrl != null) {
            return envUrl;
        }
        String engine = environmentOrDefault("DB_ENGINE", "h2");
        if ("mysql".equalsIgnoreCase(engine)) {
            return DEFAULT_MYSQL_URL;
        }
        Path dataDir = resolveLocalDataDirectory();
        if (dataDir == null) {
            return "jdbc:h2:mem:farmacia_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
        }
        String filePath = dataDir.resolve("farmacia_db").toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + filePath
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static Path resolveLocalDataDirectory() {
        String customDir = environmentOrDefault("DB_DATA_DIR", null);
        if (customDir != null) {
            Path dir = Path.of(customDir);
            if (diretorioGravavel(dir)) {
                return dir;
            }
        }
        String localAppData = environmentOrDefault("LOCALAPPDATA", null);
        if (localAppData != null) {
            Path candidate = Path.of(localAppData, "Inventory Control", "data");
            if (diretorioGravavel(candidate)) {
                return candidate;
            }
        }
        Path workspaceData = Path.of("data").toAbsolutePath();
        if (diretorioGravavel(workspaceData)) {
            return workspaceData;
        }
        Path tempFallback = Path.of(System.getProperty("java.io.tmpdir", "."), "inventory-control-data");
        if (diretorioGravavel(tempFallback)) {
            return tempFallback;
        }
        return null;
    }

    private static boolean diretorioGravavel(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = dir.resolve(".write-test");
            Files.writeString(probe, "ok");
            boolean canWrite = Files.isWritable(probe);
            Files.deleteIfExists(probe);
            if (!canWrite) {
                return false;
            }
            Path dbFile = dir.resolve("farmacia_db.mv.db");
            if (Files.exists(dbFile) && !Files.isWritable(dbFile)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void initialize() throws SQLException {

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(CREATE_PRODUCTS_TABLE);
            statement.execute(CREATE_SUPPLIERS_TABLE);
            statement.execute(CREATE_USERS_TABLE);

            garantirColunaEstoqueMinimo(connection, statement);

            statement.execute(CREATE_MOVEMENTS_TABLE);
            statement.execute(CREATE_MOVIMENTACOES_TABLE);
        }
    }

    private static void garantirColunaEstoqueMinimo(
            Connection connection,
            Statement statement
    ) throws SQLException {

        try (var columns = connection.getMetaData()
                .getColumns(null, null, "produtos", "estoque_minimo")) {

            if (!columns.next()) {
                statement.execute(
                        "ALTER TABLE produtos ADD COLUMN estoque_minimo "
                                + "INTEGER NOT NULL DEFAULT 0 "
                                + "CHECK (estoque_minimo >= 0)"
                );
            }
        }
    }

    private static String environmentOrDefault(
            String name,
            String defaultValue
    ) {

        String value = System.getenv(name);

        return value == null || value.trim().isEmpty()
                ? defaultValue
                : value;
    }
}