package com.estoque.service;

import com.estoque.entities.Fornecedor;
import com.estoque.repository.FornecedorRepository;

import java.sql.SQLException;
import java.util.List;

public class FornecedorService {
    private final FornecedorRepository fornecedorRepository;

    public FornecedorService(FornecedorRepository fornecedorRepository) {
        if (fornecedorRepository == null) {
            throw new IllegalArgumentException("O repositorio de fornecedores e obrigatorio.");
        }
        this.fornecedorRepository = fornecedorRepository;
    }

    public void cadastrar(Fornecedor fornecedor) throws SQLException {
        if (fornecedor == null) {
            throw new IllegalArgumentException("O fornecedor e obrigatorio.");
        }
        fornecedorRepository.inserir(fornecedor);
    }

    public List<Fornecedor> listar() throws SQLException {
        return fornecedorRepository.listar();
    }
}
