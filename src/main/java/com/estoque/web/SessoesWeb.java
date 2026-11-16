package com.estoque.web;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.estoque.entities.Usuario;

/** Sessoes do painel web mantidas em memoria, com expiracao por inatividade. */
final class SessoesWeb {
    static final String COOKIE = "ESTOQUE_SESSAO";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private final Duration inatividade;
    private final Clock relogio;

    SessoesWeb(Duration inatividade, Clock relogio) {
        if (inatividade == null || inatividade.isNegative() || inatividade.isZero() || relogio == null) {
            throw new IllegalArgumentException("Configuracao invalida para as sessoes web.");
        }
        this.inatividade = inatividade;
        this.relogio = relogio;
    }

    /** Cria uma nova sessao com token aleatorio para o usuario autenticado. */
    String criar(Usuario usuario) {
        limparExpiradas();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessoes.put(token, new Sessao(usuario, relogio.instant().plus(inatividade)));
        return token;
    }

    /** Retorna o usuario da sessao, renovando o prazo de inatividade. */
    Optional<Usuario> buscar(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Instant agora = relogio.instant();
        Sessao sessao = sessoes.computeIfPresent(token, (chave, atual) -> atual.expiraEm().isAfter(agora)
                ? new Sessao(atual.usuario(), agora.plus(inatividade))
                : null);
        return Optional.ofNullable(sessao).map(Sessao::usuario);
    }

    void encerrar(String token) {
        if (token != null) {
            sessoes.remove(token);
        }
    }

    private void limparExpiradas() {
        Instant agora = relogio.instant();
        sessoes.values().removeIf(sessao -> !sessao.expiraEm().isAfter(agora));
    }

    private record Sessao(Usuario usuario, Instant expiraEm) {
    }
}
