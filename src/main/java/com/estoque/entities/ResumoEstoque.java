package com.estoque.entities;

public record ResumoEstoque(
        String codigoProduto,
        String nomeProduto,
        long totalEntradas,
        long totalSaidas,
        int saldoAtual) {
}