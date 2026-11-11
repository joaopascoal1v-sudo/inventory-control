package com.estoque.app;

import com.estoque.entities.Produto;
import com.estoque.service.EstoqueService;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;

public class UI {
    private final EstoqueService estoque;
    private final JTextField codigo = new JTextField();
    private final JTextField nome = new JTextField();
    private final JTextField preco = new JTextField();
    private final JTextField quantidade = new JTextField();
    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[]{"Codigo", "Nome", "Preco", "Quantidade"}, 0) {
        @Override
        public boolean isCellEditable(int linha, int coluna) {
            return false;
        }
    };

    public UI(EstoqueService estoque) {
        if (estoque == null) {
            throw new IllegalArgumentException("O servico de estoque e obrigatorio.");
        }
        this.estoque = estoque;
    }

    public void iniciar() {
        JFrame janela = new JFrame("Controle de estoque");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(700, 450);
        janela.setLocationRelativeTo(null);

        JTable tabela = new JTable(modelo);
        tabela.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting() && tabela.getSelectedRow() >= 0) {
                preencherCampos(tabela.getSelectedRow());
            }
        });

        JPanel cadastro = new JPanel(new BorderLayout(0, 6));
        cadastro.add(criarFormulario(), BorderLayout.NORTH);
        cadastro.add(criarBotoes(), BorderLayout.SOUTH);

        janela.add(cadastro, BorderLayout.NORTH);
        janela.add(new JScrollPane(tabela), BorderLayout.CENTER);

        try {
            atualizarTabela();
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar produtos: " + e.getMessage());
        }
        janela.setVisible(true);
    }

    private JPanel criarFormulario() {
        JPanel formulario = new JPanel(new GridLayout(2, 4, 8, 4));
        formulario.add(new JLabel("Codigo"));
        formulario.add(new JLabel("Nome"));
        formulario.add(new JLabel("Preco"));
        formulario.add(new JLabel("Quantidade"));
        formulario.add(codigo);
        formulario.add(nome);
        formulario.add(preco);
        formulario.add(quantidade);
        return formulario;
    }

    private JPanel criarBotoes() {
        JPanel botoes = new JPanel();
        adicionarBotao(botoes, "Cadastrar", this::cadastrar);
        adicionarBotao(botoes, "Atualizar", this::atualizar);
        adicionarBotao(botoes, "Entrada", this::adicionarEstoque);
        adicionarBotao(botoes, "Saida", this::retirarEstoque);
        adicionarBotao(botoes, "Remover", this::remover);
        adicionarBotao(botoes, "Limpar", this::limparCampos);
        return botoes;
    }

    private void adicionarBotao(JPanel painel, String texto, Runnable acao) {
        JButton botao = new JButton(texto);
        botao.addActionListener(evento -> acao.run());
        painel.add(botao);
    }

    private void cadastrar() {
        executarAcao(() -> estoque.cadastrar(new Produto(
                lerCodigo(),
                nome.getText(),
                lerPreco(),
                lerQuantidade())), "Produto cadastrado.");
    }

    private void atualizar() {
        executarAcao(() -> estoque.atualizar(lerCodigo(), nome.getText(), lerPreco()),
                "Produto atualizado.");
    }

    private void adicionarEstoque() {
        executarAcao(() -> estoque.adicionarEstoque(lerCodigo(), lerQuantidade()),
                "Entrada registrada.");
    }

    private void retirarEstoque() {
        executarAcao(() -> estoque.retirarEstoque(lerCodigo(), lerQuantidade()),
                "Saida registrada.");
    }

    private void remover() {
        String codigoProduto = lerCodigo();
        if (codigoProduto.isEmpty()) {
            mostrarErro("Digite ou selecione o codigo do produto que deseja remover.");
            return;
        }

        int resposta = JOptionPane.showConfirmDialog(
                null, "Deseja remover o produto " + codigoProduto + "?", "Confirmar remocao",
                JOptionPane.YES_NO_OPTION);
        if (resposta == JOptionPane.YES_OPTION) {
            if (executarAcao(() -> estoque.remover(codigoProduto), "Produto removido.")) {
                limparCampos();
            }
        }
    }

    private boolean executarAcao(Acao acao, String mensagem) {
        try {
            acao.executar();
            atualizarTabela();
            JOptionPane.showMessageDialog(null, mensagem);
            return true;
        } catch (IllegalArgumentException | ArithmeticException e) {
            mostrarErro(e.getMessage());
        } catch (SQLException e) {
            mostrarErro("Erro ao acessar o banco de dados: " + e.getMessage());
        }
        return false;
    }

    private void atualizarTabela() throws SQLException {
        modelo.setRowCount(0);
        for (Produto produto : estoque.listar()) {
            modelo.addRow(new Object[]{
                    produto.getCodigo(),
                    produto.getNome(),
                    produto.getPreco().toPlainString(),
                    produto.getQuantidade()
            });
        }
    }

    private void preencherCampos(int linha) {
        codigo.setText(modelo.getValueAt(linha, 0).toString());
        nome.setText(modelo.getValueAt(linha, 1).toString());
        preco.setText(modelo.getValueAt(linha, 2).toString());
        quantidade.setText(modelo.getValueAt(linha, 3).toString());
    }

    private String lerCodigo() {
        return codigo.getText().trim();
    }

    private BigDecimal lerPreco() {
        return new BigDecimal(preco.getText().trim().replace(',', '.'));
    }

    private int lerQuantidade() {
        return Integer.parseInt(quantidade.getText().trim());
    }

    private void limparCampos() {
        codigo.setText("");
        nome.setText("");
        preco.setText("");
        quantidade.setText("");
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(null, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws SQLException;
    }
}
