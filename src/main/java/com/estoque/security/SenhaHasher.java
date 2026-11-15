package com.estoque.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SenhaHasher {
    private static final int ITERACOES = 120000;
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SenhaHasher() {
    }

    public static String gerar(String senha) {
        validarSenha(senha);
        byte[] salt = new byte[TAMANHO_SALT];
        RANDOM.nextBytes(salt);
        byte[] hash = derivar(senha.toCharArray(), salt, ITERACOES);
        return ITERACOES + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(String senha, String hashArmazenado) {
        if (senha == null || hashArmazenado == null) {
            return false;
        }
        String[] partes = hashArmazenado.split(":", -1);
        if (partes.length != 3) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[0]);
            byte[] salt = Base64.getDecoder().decode(partes[1]);
            byte[] hashEsperado = Base64.getDecoder().decode(partes[2]);
            if (iteracoes <= 0 || salt.length == 0 || hashEsperado.length == 0) {
                return false;
            }
            byte[] hashRecebido = derivar(senha.toCharArray(), salt, iteracoes);
            if (hashRecebido.length != hashEsperado.length) {
                return false;
            }
            int diferenca = 0;
            for (int indice = 0; indice < hashEsperado.length; indice++) {
                diferenca |= hashRecebido[indice] ^ hashEsperado[indice];
            }
            return diferenca == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] senha, byte[] salt, int iteracoes) {
        PBEKeySpec especificacao = new PBEKeySpec(senha, salt, iteracoes, TAMANHO_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(especificacao).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nao foi possivel proteger a senha.", e);
        } finally {
            especificacao.clearPassword();
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        }
    }
}
