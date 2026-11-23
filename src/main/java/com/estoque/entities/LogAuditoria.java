package com.estoque.entities;

import java.time.LocalDateTime;

public record LogAuditoria(
        long id,
        LocalDateTime dataHora,
        String usuarioNome,
        String usuarioEmail,
        String usuarioPerfil,
        String categoria,
        String acao,
        String detalhes,
        String origem
) {
    public boolean isAcesso() {
        return "ACESSO".equalsIgnoreCase(categoria);
    }

    public boolean isModificacao() {
        return "MODIFICACAO".equalsIgnoreCase(categoria);
    }

    public String classeSelo() {
        if (acao == null) {
            return "selo-normal";
        }
        String a = acao.toLowerCase();
        if (a.contains("exclus") || a.contains("remo") || a.contains("falha") || a.contains("saída de estoque")) {
            return "selo-esgotado";
        }
        if (a.contains("estorno") || a.contains("ediç") || a.contains("altera")) {
            return "selo-baixo";
        }
        return "selo-normal";
    }
}
