package com.estoque.app;

import com.estoque.entities.Fornecedor;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.PerfilAcesso;
import com.estoque.entities.Produto;
import com.estoque.entities.ProdutoMovimentado;
import com.estoque.entities.TipoMovimentacao;
import com.estoque.entities.Usuario;
import com.estoque.repository.MovimentacaoRepositoryJdbc;
import com.estoque.service.EstoqueService;
import com.estoque.service.FornecedorService;
import com.estoque.service.RelatorioExportacaoService;
import com.estoque.service.UsuarioService;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public class UI {

    private final EstoqueService estoque;
    private final FornecedorService fornecedores;
    private final UsuarioService usuarios;
    private final RelatorioExportacaoService relatorioExportacao;

    private final MovimentacaoRepositoryJdbc movimentacaoRepository =
            new MovimentacaoRepositoryJdbc();

    private Usuario usuarioAutenticado;

    private final JTextField codigo = new JTextField();
    private final JTextField nomeProduto = new JTextField();
    private final JTextField preco = new JTextField();
    private final JTextField quantidade = new JTextField();
    private final JTextField estoqueMinimo = new JTextField("0");

    private final JLabel alertaEstoque = new JLabel(" ");

    private final JTextField nomeFornecedor = new JTextField();
    private final JTextField cnpjFornecedor = new JTextField();
    private final JTextField telefoneFornecedor = new JTextField();
    private final JTextField emailFornecedor = new JTextField();
    private final JTextField enderecoFornecedor = new JTextField();

    private final DefaultTableModel modeloProdutos =
            new DefaultTableModel(
                    new Object[]{
                            "Codigo",
                            "Nome",
                            "Preco",
                            "Quantidade",
                            "Estoque minimo"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloFornecedores =
            new DefaultTableModel(
                    new Object[]{
                            "Nome",
                            "CNPJ",
                            "Telefone",
                            "E-mail",
                            "Endereco"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloEstoqueBaixo =
            new DefaultTableModel(
                    new Object[]{"Codigo", "Produto", "Quantidade"}, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloMaisMovimentados =
            new DefaultTableModel(
                    new Object[]{
                            "Codigo",
                            "Produto",
                            "Quantidade movimentada"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    public UI(
            EstoqueService estoque,
            FornecedorService fornecedores,
            UsuarioService usuarios
    ) {
        if (estoque == null || fornecedores == null || usuarios == null) {
            throw new IllegalArgumentException(
                    "Os servicos do sistema sao obrigatorios.");
        }

        this.estoque = estoque;
        this.fornecedores = fornecedores;
        this.usuarios = usuarios;

        this.relatorioExportacao =
                new RelatorioExportacaoService(estoque);
    }

    public void iniciar() {

        Usuario usuario = autenticar();

        if (usuario == null) {
            return;
        }

        usuarioAutenticado = usuario;

        JFrame janela = new JFrame(
                "Controle de estoque - "
                        + usuario.getNome()
                        + " (" + nomePerfil(usuario.getPerfil()) + ")");

        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(1000, 650);
        janela.setLocationRelativeTo(null);

        JPanel cabecalho = new JPanel(new BorderLayout());

        cabecalho.add(
                new JLabel(
                        "  Usuario: " + usuario.getNome()
                                + " | Perfil: "
                                + nomePerfil(usuario.getPerfil())),
                BorderLayout.WEST);

        if (usuario.getPerfil() == PerfilAcesso.ADMIN) {

            JButton cadastrarFuncionario =
                    new JButton("Cadastrar funcionario");

            cadastrarFuncionario.addActionListener(
                    evento -> cadastrarFuncionario(usuario));

            JPanel acoes = new JPanel(
                    new FlowLayout(FlowLayout.RIGHT));

            acoes.add(cadastrarFuncionario);

            cabecalho.add(acoes, BorderLayout.EAST);
        }

        JTabbedPane abas = new JTabbedPane();

        abas.addTab(
                "Produtos e estoque",
                criarPainelProdutos());

        abas.addTab(
                "Fornecedores",
                criarPainelFornecedores());

        abas.addTab(
                "Relatorios",
                criarPainelRelatorios());

        janela.add(cabecalho, BorderLayout.NORTH);
        janela.add(abas, BorderLayout.CENTER);

        try {
            atualizarTabelaProdutos();
            atualizarTabelaFornecedores();
            atualizarRelatorios();
        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao carregar dados: " + e.getMessage());
        }

        janela.setVisible(true);
    }

    private Usuario autenticar() {

        while (true) {

            try {
                if (!usuarios.temUsuarios()) {
                    return cadastrarAdministradorInicial();
                }
            } catch (SQLException e) {
                mostrarErro(
                        "Nao foi possivel verificar os usuarios no banco: "
                                + e.getMessage());
                return null;
            }

            JTextField email = new JTextField(22);
            JPasswordField senha = new JPasswordField(22);

            JPanel formulario = new JPanel(
                    new GridLayout(2, 2, 8, 8));

            formulario.add(new JLabel("E-mail"));
            formulario.add(email);
            formulario.add(new JLabel("Senha"));
            formulario.add(senha);

            int resposta = JOptionPane.showConfirmDialog(
                    null,
                    formulario,
                    "Login",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (resposta != JOptionPane.OK_OPTION) {
                return null;
            }

            char[] senhaDigitada = senha.getPassword();

            try {
                return usuarios.login(
                        email.getText(),
                        new String(senhaDigitada));

            } catch (IllegalArgumentException e) {
                mostrarErro(e.getMessage());

            } catch (SQLException e) {
                mostrarErro(
                        "Erro ao acessar o banco de dados: "
                                + e.getMessage());
                return null;

            } finally {
                Arrays.fill(senhaDigitada, '\0');
                senha.setText("");
            }
        }
    }

    private Usuario cadastrarAdministradorInicial() {

        JTextField nome = new JTextField(22);
        JTextField email = new JTextField(22);
        JPasswordField senha = new JPasswordField(22);
        JPasswordField confirmarSenha = new JPasswordField(22);

        JPanel formulario = new JPanel(
                new GridLayout(4, 2, 8, 8));

        formulario.add(new JLabel("Nome do administrador"));
        formulario.add(nome);

        formulario.add(new JLabel("E-mail"));
        formulario.add(email);

        formulario.add(new JLabel("Senha (minimo 8 caracteres)"));
        formulario.add(senha);

        formulario.add(new JLabel("Confirmar senha"));
        formulario.add(confirmarSenha);

        while (true) {

            int resposta = JOptionPane.showConfirmDialog(
                    null,
                    formulario,
                    "Criar primeiro administrador",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);

            if (resposta != JOptionPane.OK_OPTION) {
                return null;
            }

            char[] senhaDigitada = senha.getPassword();
            char[] confirmacao = confirmarSenha.getPassword();

            try {

                if (!Arrays.equals(senhaDigitada, confirmacao)) {
                    mostrarErro("As senhas nao conferem.");
                    continue;
                }

                Usuario administrador =
                        usuarios.cadastrarPrimeiroAdministrador(
                                nome.getText(),
                                email.getText(),
                                new String(senhaDigitada));

                JOptionPane.showMessageDialog(
                        null,
                        "Administrador cadastrado. Voce ja esta conectado.");

                return administrador;

            } catch (IllegalArgumentException e) {
                mostrarErro(e.getMessage());

            } catch (SQLException e) {
                mostrarErro(
                        "Erro ao cadastrar administrador: "
                                + e.getMessage());
                return null;

            } finally {
                Arrays.fill(senhaDigitada, '\0');
                Arrays.fill(confirmacao, '\0');
                senha.setText("");
                confirmarSenha.setText("");
            }
        }
    }

    private JPanel criarPainelProdutos() {

        JPanel painel = new JPanel(
                new BorderLayout(0, 6));

        JTable tabela = new JTable(modeloProdutos);

        tabela.getSelectionModel().addListSelectionListener(
                evento -> {
                    if (!evento.getValueIsAdjusting()
                            && tabela.getSelectedRow() >= 0) {
                        preencherCamposProduto(
                                tabela.getSelectedRow());
                    }
                });

        JPanel cadastro = new JPanel(
                new BorderLayout(0, 6));

        cadastro.add(
                criarFormularioProduto(),
                BorderLayout.NORTH);

        JPanel acoes = new JPanel(new BorderLayout());

        acoes.add(
                criarBotoesProduto(),
                BorderLayout.NORTH);

        acoes.add(
                alertaEstoque,
                BorderLayout.SOUTH);

        cadastro.add(acoes, BorderLayout.SOUTH);

        painel.add(cadastro, BorderLayout.NORTH);

        painel.add(
                new JScrollPane(tabela),
                BorderLayout.CENTER);

        return painel;
    }

    private JPanel criarFormularioProduto() {

        JPanel formulario = new JPanel(
                new GridLayout(2, 5, 8, 4));

        formulario.add(new JLabel("Codigo"));
        formulario.add(new JLabel("Nome"));
        formulario.add(new JLabel("Preco"));
        formulario.add(new JLabel("Quantidade"));
        formulario.add(new JLabel("Estoque minimo"));

        formulario.add(codigo);
        formulario.add(nomeProduto);
        formulario.add(preco);
        formulario.add(quantidade);
        formulario.add(estoqueMinimo);

        return formulario;
    }

    private JPanel criarBotoesProduto() {

        JPanel botoes = new JPanel();

        adicionarBotao(
                botoes, "Cadastrar", this::cadastrarProduto);

        adicionarBotao(
                botoes, "Atualizar", this::atualizarProduto);

        adicionarBotao(
                botoes, "Entrada", this::adicionarEstoque);

        adicionarBotao(
                botoes, "Saida", this::retirarEstoque);

        adicionarBotao(
                botoes, "Remover", this::removerProduto);

        adicionarBotao(
                botoes, "Limpar", this::limparCamposProduto);

        return botoes;
    }

    private JPanel criarPainelFornecedores() {

        JPanel painel = new JPanel(
                new BorderLayout(0, 6));

        JPanel formulario = new JPanel(
                new GridLayout(2, 5, 8, 4));

        formulario.add(new JLabel("Nome *"));
        formulario.add(new JLabel("CNPJ"));
        formulario.add(new JLabel("Telefone"));
        formulario.add(new JLabel("E-mail"));
        formulario.add(new JLabel("Endereco"));

        formulario.add(nomeFornecedor);
        formulario.add(cnpjFornecedor);
        formulario.add(telefoneFornecedor);
        formulario.add(emailFornecedor);
        formulario.add(enderecoFornecedor);

        JPanel botoes = new JPanel();

        adicionarBotao(
                botoes,
                "Cadastrar fornecedor",
                this::cadastrarFornecedor);

        adicionarBotao(
                botoes,
                "Limpar",
                this::limparCamposFornecedor);

        adicionarBotao(
                botoes,
                "Atualizar lista",
                this::atualizarTabelaFornecedores);

        JPanel topo = new JPanel(
                new BorderLayout(0, 6));

        topo.add(formulario, BorderLayout.NORTH);
        topo.add(botoes, BorderLayout.SOUTH);

        painel.add(topo, BorderLayout.NORTH);

        painel.add(
                new JScrollPane(
                        new JTable(modeloFornecedores)),
                BorderLayout.CENTER);

        return painel;
    }

    private JPanel criarPainelRelatorios() {

        JPanel painel = new JPanel(
                new BorderLayout(8, 8));

        JTable tabelaBaixo =
                new JTable(modeloEstoqueBaixo);

        JTable tabelaMovimentados =
                new JTable(modeloMaisMovimentados);

        JPanel tabelas = new JPanel(
                new GridLayout(2, 1, 8, 8));

        JPanel baixo = new JPanel(
                new BorderLayout());

        baixo.add(
                new JLabel("Produtos com estoque baixo"),
                BorderLayout.NORTH);

        baixo.add(
                new JScrollPane(tabelaBaixo),
                BorderLayout.CENTER);

        JPanel movimentados = new JPanel(
                new BorderLayout());

        movimentados.add(
                new JLabel("Produtos mais movimentados"),
                BorderLayout.NORTH);

        movimentados.add(
                new JScrollPane(tabelaMovimentados),
                BorderLayout.CENTER);

        tabelas.add(baixo);
        tabelas.add(movimentados);

        JButton atualizar = new JButton("Atualizar relatorios");

        atualizar.addActionListener(
                evento -> atualizarRelatorios());

        JButton exportarPdf = new JButton("Exportar PDF");

        exportarPdf.addActionListener(
                evento -> exportarPdf());

        JButton exportarExcel = new JButton("Exportar Excel");

        exportarExcel.addActionListener(
                evento -> exportarExcel());

        JPanel botoes = new JPanel(
                new FlowLayout(FlowLayout.RIGHT));

        botoes.add(atualizar);
        botoes.add(exportarPdf);
        botoes.add(exportarExcel);

        painel.add(tabelas, BorderLayout.CENTER);
        painel.add(botoes, BorderLayout.SOUTH);

        return painel;
    }

    private void exportarPdf() {

        JFileChooser seletor = new JFileChooser();

        seletor.setDialogTitle("Salvar relatorio PDF");

        seletor.setSelectedFile(
                new File("relatorio-estoque.pdf"));

        seletor.setFileFilter(
                new FileNameExtensionFilter(
                        "Arquivo PDF (*.pdf)", "pdf"));

        if (seletor.showSaveDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File arquivo = seletor.getSelectedFile();

        if (!arquivo.getName().toLowerCase().endsWith(".pdf")) {
            arquivo = new File(
                    arquivo.getAbsolutePath() + ".pdf");
        }

        if (arquivo.exists()) {

            int resposta = JOptionPane.showConfirmDialog(
                    null,
                    "O arquivo ja existe. Deseja substituir?",
                    "Confirmar",
                    JOptionPane.YES_NO_OPTION);

            if (resposta != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {

            relatorioExportacao.exportarPdf(
                    arquivo.toPath());

            JOptionPane.showMessageDialog(
                    null,
                    "PDF exportado com sucesso!\n"
                            + arquivo.getAbsolutePath());

        } catch (SQLException | IOException e) {
            mostrarErro(
                    "Erro ao exportar PDF: "
                            + e.getMessage());
        }
    }

    private void exportarExcel() {

        JFileChooser seletor = new JFileChooser();

        seletor.setDialogTitle("Salvar relatorio Excel");

        seletor.setSelectedFile(
                new File("relatorio-estoque.xlsx"));

        seletor.setFileFilter(
                new FileNameExtensionFilter(
                        "Arquivo Excel (*.xlsx)", "xlsx"));

        if (seletor.showSaveDialog(null)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File arquivo = seletor.getSelectedFile();

        if (!arquivo.getName().toLowerCase().endsWith(".xlsx")) {
            arquivo = new File(
                    arquivo.getAbsolutePath() + ".xlsx");
        }

        if (arquivo.exists()) {

            int resposta = JOptionPane.showConfirmDialog(
                    null,
                    "O arquivo ja existe. Deseja substituir?",
                    "Confirmar",
                    JOptionPane.YES_NO_OPTION);

            if (resposta != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {

            relatorioExportacao.exportarExcel(
                    arquivo.toPath());

            JOptionPane.showMessageDialog(
                    null,
                    "Excel exportado com sucesso!\n"
                            + arquivo.getAbsolutePath());

        } catch (SQLException | IOException e) {
            mostrarErro(
                    "Erro ao exportar Excel: "
                            + e.getMessage());
        }
    }

    private void adicionarBotao(
            JPanel painel,
            String texto,
            Runnable acao
    ) {

        JButton botao = new JButton(texto);

        botao.addActionListener(
                evento -> acao.run());

        painel.add(botao);
    }

    private void cadastrarProduto() {

        executarAcao(
                () -> estoque.cadastrar(
                        new Produto(
                                lerCodigo(),
                                nomeProduto.getText(),
                                lerPreco(),
                                lerQuantidade(),
                                lerEstoqueMinimo())),
                "Produto cadastrado.");
    }

    private void atualizarProduto() {

        executarAcao(
                () -> estoque.atualizar(
                        lerCodigo(),
                        nomeProduto.getText(),
                        lerPreco(),
                        lerEstoqueMinimo()),
                "Produto atualizado.");
    }

    private void adicionarEstoque() {

        executarAcao(
                () -> estoque.registrarMovimentacao(
                        lerCodigo(),
                        TipoMovimentacao.ENTRADA,
                        null,
                        lerQuantidade(),
                        MotivoMovimentacao.COMPRA,
                        usuarioAutenticado.getId(),
                        null),
                "Entrada registrada.");
    }

    private void retirarEstoque() {

        executarAcao(
                () -> estoque.registrarMovimentacao(
                        lerCodigo(),
                        TipoMovimentacao.SAIDA,
                        null,
                        lerQuantidade(),
                        MotivoMovimentacao.VENDA,
                        usuarioAutenticado.getId(),
                        null),
                "Saida registrada.");
    }

    private void removerProduto() {

        String codigoProduto = lerCodigo();

        if (codigoProduto.isEmpty()) {
            mostrarErro(
                    "Digite ou selecione o codigo do produto que deseja remover.");
            return;
        }

        int resposta = JOptionPane.showConfirmDialog(
                null,
                "Deseja remover o produto " + codigoProduto + "?",
                "Confirmar remocao",
                JOptionPane.YES_NO_OPTION);

        if (resposta == JOptionPane.YES_OPTION
                && executarAcao(
                        () -> estoque.remover(codigoProduto),
                        "Produto removido.")) {

            limparCamposProduto();
        }
    }

    private void cadastrarFornecedor() {

        try {

            fornecedores.cadastrar(
                    new Fornecedor(
                            nomeFornecedor.getText(),
                            cnpjFornecedor.getText(),
                            telefoneFornecedor.getText(),
                            emailFornecedor.getText(),
                            enderecoFornecedor.getText()));

            atualizarTabelaFornecedores();
            limparCamposFornecedor();

            JOptionPane.showMessageDialog(
                    null,
                    "Fornecedor cadastrado.");

        } catch (IllegalArgumentException e) {
            mostrarErro(e.getMessage());

        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao cadastrar fornecedor: "
                            + e.getMessage());
        }
    }

    private void cadastrarFuncionario(
            Usuario administrador
    ) {

        JTextField nome = new JTextField(22);
        JTextField email = new JTextField(22);
        JPasswordField senha = new JPasswordField(22);
        JPasswordField confirmarSenha = new JPasswordField(22);

        JPanel formulario = new JPanel(
                new GridLayout(4, 2, 8, 8));

        formulario.add(new JLabel("Nome"));
        formulario.add(nome);

        formulario.add(new JLabel("E-mail"));
        formulario.add(email);

        formulario.add(new JLabel("Senha (minimo 8 caracteres)"));
        formulario.add(senha);

        formulario.add(new JLabel("Confirmar senha"));
        formulario.add(confirmarSenha);

        int resposta = JOptionPane.showConfirmDialog(
                null,
                formulario,
                "Cadastrar funcionario",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (resposta != JOptionPane.OK_OPTION) {
            return;
        }

        char[] senhaDigitada = senha.getPassword();
        char[] confirmacao = confirmarSenha.getPassword();

        try {

            if (!Arrays.equals(senhaDigitada, confirmacao)) {
                mostrarErro("As senhas nao conferem.");
                return;
            }

            usuarios.cadastrarFuncionario(
                    administrador,
                    nome.getText(),
                    email.getText(),
                    new String(senhaDigitada));

            JOptionPane.showMessageDialog(
                    null,
                    "Funcionario cadastrado.");

        } catch (IllegalArgumentException e) {
            mostrarErro(e.getMessage());

        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao cadastrar funcionario: "
                            + e.getMessage());

        } finally {
            Arrays.fill(senhaDigitada, '\0');
            Arrays.fill(confirmacao, '\0');
            senha.setText("");
            confirmarSenha.setText("");
        }
    }

    private boolean executarAcao(
            Acao acao,
            String mensagem
    ) {

        try {

            acao.executar();

            atualizarTabelaProdutos();
            atualizarRelatorios();

            JOptionPane.showMessageDialog(
                    null,
                    mensagem);

            return true;

        } catch (IllegalArgumentException | ArithmeticException e) {
            mostrarErro(e.getMessage());

        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao acessar o banco de dados: "
                            + e.getMessage());
        }

        return false;
    }

    private void atualizarTabelaProdutos()
            throws SQLException {

        modeloProdutos.setRowCount(0);

        for (Produto produto : estoque.listar()) {

            modeloProdutos.addRow(
                    new Object[]{
                            produto.getCodigo(),
                            produto.getNome(),
                            produto.getPreco().toPlainString(),
                            produto.getQuantidade(),
                            produto.getEstoqueMinimo()
                    });
        }

        var alertas =
                estoque.listarAlertasEstoqueMinimo();

        alertaEstoque.setText(
                alertas.isEmpty()
                        ? " "
                        : "Estoque abaixo do minimo: "
                                + String.join(
                                        ", ",
                                        alertas.stream()
                                                .map(Produto::getNome)
                                                .toList()));
    }

    private void atualizarTabelaFornecedores() {

        try {

            modeloFornecedores.setRowCount(0);

            for (Fornecedor fornecedor :
                    fornecedores.listar()) {

                modeloFornecedores.addRow(
                        new Object[]{
                                fornecedor.getNome(),
                                valorOuVazio(fornecedor.getCnpj()),
                                valorOuVazio(fornecedor.getTelefone()),
                                valorOuVazio(fornecedor.getEmail()),
                                valorOuVazio(fornecedor.getEndereco())
                        });
            }

        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao carregar fornecedores: "
                            + e.getMessage());
        }
    }

    private void atualizarRelatorios() {

        try {

            modeloEstoqueBaixo.setRowCount(0);
            modeloMaisMovimentados.setRowCount(0);

            for (Produto produto : estoque.listar()) {

                if (produto.getQuantidade()
                        <= produto.getEstoqueMinimo()) {

                    modeloEstoqueBaixo.addRow(
                            new Object[]{
                                    produto.getCodigo(),
                                    produto.getNome(),
                                    produto.getQuantidade()
                            });
                }
            }

            for (ProdutoMovimentado produto :
                    movimentacaoRepository.listarMaisMovimentados()) {

                modeloMaisMovimentados.addRow(
                        new Object[]{
                                produto.getCodigo(),
                                produto.getNome(),
                                produto.getQuantidadeMovimentada()
                        });
            }

        } catch (SQLException e) {
            mostrarErro(
                    "Erro ao carregar relatorios: "
                            + e.getMessage());
        }
    }

    private void preencherCamposProduto(int linha) {

        codigo.setText(
                modeloProdutos.getValueAt(linha, 0).toString());

        nomeProduto.setText(
                modeloProdutos.getValueAt(linha, 1).toString());

        preco.setText(
                modeloProdutos.getValueAt(linha, 2).toString());

        quantidade.setText(
                modeloProdutos.getValueAt(linha, 3).toString());

        estoqueMinimo.setText(
                modeloProdutos.getValueAt(linha, 4).toString());
    }

    private String lerCodigo() {
        return codigo.getText().trim();
    }

    private BigDecimal lerPreco() {
        return new BigDecimal(
                preco.getText().trim().replace(',', '.'));
    }

    private int lerQuantidade() {
        return Integer.parseInt(
                quantidade.getText().trim());
    }

    private int lerEstoqueMinimo() {
        return Integer.parseInt(
                estoqueMinimo.getText().trim());
    }

    private void limparCamposProduto() {

        codigo.setText("");
        nomeProduto.setText("");
        preco.setText("");
        quantidade.setText("");
        estoqueMinimo.setText("0");
    }

    private void limparCamposFornecedor() {

        nomeFornecedor.setText("");
        cnpjFornecedor.setText("");
        telefoneFornecedor.setText("");
        emailFornecedor.setText("");
        enderecoFornecedor.setText("");
    }

    private String nomePerfil(PerfilAcesso perfil) {

        return perfil == PerfilAcesso.ADMIN
                ? "Administrador"
                : "Funcionario";
    }

    private String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private void mostrarErro(String mensagem) {

        JOptionPane.showMessageDialog(
                null,
                mensagem,
                "Erro",
                JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws SQLException;
    }
}