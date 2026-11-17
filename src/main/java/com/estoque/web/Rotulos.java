package com.estoque.web;

import java.util.Arrays;
import java.util.List;

import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.TipoMovimentacao;

/** Rotulos em portugues para os enums exibidos nas telas web. */
public final class Rotulos {

    private Rotulos() {
    }

    public static String tipo(TipoMovimentacao tipo) {
        return switch (tipo) {
            case ENTRADA -> "Entrada";
            case SAIDA -> "Saída";
            case AJUSTE -> "Ajuste";
            case PERDA -> "Perda";
            case DEVOLUCAO -> "Devolução";
            case TRANSFERENCIA -> "Transferência";
        };
    }

    public static String motivo(MotivoMovimentacao motivo) {
        return switch (motivo) {
            case COMPRA -> "Compra";
            case VENDA -> "Venda";
            case DEVOLUCAO -> "Devolução";
            case AJUSTE -> "Ajuste";
            case PERDA -> "Perda";
            case TRANSFERENCIA -> "Transferência";
            case ESTORNO -> "Estorno";
            case OUTRO -> "Outro";
        };
    }

    public static String perfil(PerfilAcesso perfil) {
        return switch (perfil) {
            case ADMIN -> "Administrador";
            case FUNCIONARIO -> "Funcionário";
        };
    }

    /** Opcoes do filtro de tipo da tela de movimentacoes. */
    public static List<Opcao> tiposMovimentacao() {
        return Arrays.stream(TipoMovimentacao.values())
                .map(tipo -> new Opcao(tipo.name(), tipo(tipo)))
                .toList();
    }

    public record Opcao(String valor, String rotulo) {
    }
}
