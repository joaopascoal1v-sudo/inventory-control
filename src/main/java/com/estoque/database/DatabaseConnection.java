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
                    + "quantidade INTEGER NOT NULL CHECK (quantidade >= 0)"
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
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
