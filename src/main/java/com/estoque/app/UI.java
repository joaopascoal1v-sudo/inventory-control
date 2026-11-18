package com.estoque.app;

import com.estoque.entities.Fornecedor;
import com.estoque.entities.MotivoMovimentacao;
import com.estoque.entities.MovimentacaoEstoque;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
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
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

public class UI {

    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color BG_APP = new Color(241, 245, 249);
    private static final Color BG_CARD = Color.WHITE;
    private static final Color BG_HEADER = new Color(15, 23, 42);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_PRIMARY = new Color(30, 41, 59);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);

    private static final Color COLOR_PRIMARY = new Color(37, 99, 235);
    private static final Color COLOR_INDIGO = new Color(79, 70, 229);
    private static final Color COLOR_SUCCESS = new Color(22, 163, 74);
    private static final Color COLOR_WARNING = new Color(217, 119, 6);
    private static final Color COLOR_DANGER = new Color(220, 38, 38);
    private static final Color COLOR_SECONDARY = new Color(71, 85, 105);
    private static final Color COLOR_NEUTRAL = new Color(226, 232, 240);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 19);
    private static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_KPI_VALUE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 12);

    private final EstoqueService estoque;
    private final FornecedorService fornecedores;
    private final UsuarioService usuarios;
    private final RelatorioExportacaoService relatorioExportacao;

    private final MovimentacaoRepositoryJdbc movimentacaoRepository =
            new MovimentacaoRepositoryJdbc();

    private Usuario usuarioAutenticado;
    private Process processoTunel;
    private volatile String linkPublicoAtual;

    private final JTextField codigo = criarCampoTexto("Ex: MED001");
    private final JTextField nomeProduto = criarCampoTexto("Ex: Dipirona 500mg");
    private final JTextField preco = criarCampoTexto("Ex: 8.90");
    private final JTextField quantidade = criarCampoTexto("Ex: 20");
    private final JTextField estoqueMinimo = criarCampoTexto("Ex: 10");
    private final JTextField buscaProduto = criarCampoTexto("Digite o código ou nome do medicamento...");

    private final JLabel alertaEstoque = new JLabel(" ");
    private final JLabel kpiTotalProdutos = new JLabel("0");
    private final JLabel kpiTotalUnidades = new JLabel("0");
    private final JLabel kpiValorTotal = new JLabel("R$ 0,00");
    private final JLabel kpiTotalAlertas = new JLabel("0");

    private final JTextField nomeFornecedor = criarCampoTexto("Nome da empresa");
    private final JTextField cnpjFornecedor = criarCampoTexto("Opcional");
    private final JTextField telefoneFornecedor = criarCampoTexto("Opcional");
    private final JTextField emailFornecedor = criarCampoTexto("Opcional");
    private final JTextField enderecoFornecedor = criarCampoTexto("Opcional");

    private final DefaultTableModel modeloProdutos =
            new DefaultTableModel(
                    new Object[]{
                            "Código",
                            "Produto",
                            "Preço (R$)",
                            "Em Estoque",
                            "Estoque Mínimo"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloMovimentacoes =
            new DefaultTableModel(
                    new Object[]{
                            "ID",
                            "Data / Hora",
                            "Código",
                            "Operação",
                            "Motivo",
                            "Qtd",
                            "Saldo Antes → Depois",
                            "Status"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloFornecedores =
            new DefaultTableModel(
                    new Object[]{
                            "Nome / Empresa",
                            "CNPJ",
                            "Telefone",
                            "E-mail",
                            "Endereço"
                    }, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloEstoqueBaixo =
            new DefaultTableModel(
                    new Object[]{"Código", "Produto", "Qtd. Atual"}, 0) {
                @Override
                public boolean isCellEditable(int linha, int coluna) {
                    return false;
                }
            };

    private final DefaultTableModel modeloMaisMovimentados =
            new DefaultTableModel(
                    new Object[]{
                            "Código",
                            "Produto",
                            "Total de Saídas"
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
        this.relatorioExportacao = new RelatorioExportacaoService(estoque);
        this.estoqueMinimo.setText("0");
    }

    public void iniciar() {
        configurarTemaModerno();

        Usuario usuario = autenticar();
        if (usuario == null) {
            System.exit(0);
            return;
        }

        usuarioAutenticado = usuario;

        JFrame janela = new JFrame("Inventory Control — Sistema de Estoque para Farmácia (Desktop + Web Online)");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setSize(1160, 760);
        janela.setMinimumSize(new Dimension(960, 640));
        janela.setLocationRelativeTo(null);
        janela.getContentPane().setBackground(BG_APP);
        janela.setLayout(new BorderLayout(0, 0));

        JPanel topoContainer = new JPanel(new BorderLayout(0, 0));
        topoContainer.setBackground(BG_APP);
        topoContainer.add(criarCabecalho(usuario), BorderLayout.NORTH);
        topoContainer.add(criarPainelIndicadoresKpi(), BorderLayout.SOUTH);
        janela.add(topoContainer, BorderLayout.NORTH);

        JTabbedPane abas = new JTabbedPane();
        abas.setFont(FONT_SUBTITLE);
        abas.setBackground(BG_APP);
        abas.setBorder(new EmptyBorder(6, 16, 16, 16));

        abas.addTab("  📦 Produtos e Estoque  ", criarPainelProdutos());
        abas.addTab("  🕒 Histórico e Estornos  ", criarPainelHistorico());
        abas.addTab("  🚚 Fornecedores  ", criarPainelFornecedores());
        abas.addTab("  📊 Relatórios (PDF / Excel)  ", criarPainelRelatorios());

        janela.add(abas, BorderLayout.CENTER);

        try {
            atualizarTabelaProdutos();
            atualizarTabelaFornecedores();
            atualizarRelatorios();
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar dados: " + e.getMessage());
        }

        janela.setVisible(true);
    }

    private void configurarTemaModerno() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("OptionPane.messageFont", FONT_REGULAR);
            UIManager.put("OptionPane.buttonFont", FONT_BUTTON);
        } catch (Exception ignored) {
        }
    }

    private JPanel criarCabecalho(Usuario usuario) {
        JPanel cabecalho = new JPanel(new BorderLayout(16, 0));
        cabecalho.setBackground(BG_HEADER);
        cabecalho.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel esquerda = new JPanel();
        esquerda.setOpaque(false);
        esquerda.setLayout(new BoxLayout(esquerda, BoxLayout.Y_AXIS));

        JLabel titulo = new JLabel("Inventory Control • Farmácia");
        titulo.setFont(FONT_TITLE);
        titulo.setForeground(Color.WHITE);

        JLabel subtitulo = new JLabel(
                "Conectado como " + usuario.getNome() + " (" + nomePerfil(usuario.getPerfil()) + ")  •  Servidor Web Ativo na porta "
                        + System.getenv().getOrDefault("WEB_PORT", "8081"));
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitulo.setForeground(new Color(148, 163, 184));

        esquerda.add(titulo);
        esquerda.add(Box.createVerticalStrut(2));
        esquerda.add(subtitulo);

        cabecalho.add(esquerda, BorderLayout.WEST);

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        direita.setOpaque(false);

        JButton btnCompartilharOnline = criarBotaoEstilizado(
                "🔗 Gerar Link Online (WhatsApp)",
                COLOR_INDIGO,
                Color.WHITE);
        btnCompartilharOnline.setToolTipText("Gera um link público HTTPS na hora para qualquer pessoa acessar de qualquer lugar");
        btnCompartilharOnline.addActionListener(evento -> mostrarDialogoServidorOnline());
        direita.add(btnCompartilharOnline);

        JButton abrirWeb = criarBotaoEstilizado(
                "🌐 Abrir no Navegador",
                COLOR_SUCCESS,
                Color.WHITE);
        abrirWeb.setToolTipText("Abre o painel web no seu navegador (http://localhost:8081)");
        abrirWeb.addActionListener(evento -> abrirPainelWebNoNavegador());
        direita.add(abrirWeb);

        if (usuario.getPerfil() == PerfilAcesso.ADMIN) {
            JButton cadastrarFuncionario = criarBotaoEstilizado(
                    "+ Novo Usuário",
                    COLOR_PRIMARY,
                    Color.WHITE);
            cadastrarFuncionario.addActionListener(evento -> cadastrarFuncionario(usuario));
            direita.add(cadastrarFuncionario);
        }

        cabecalho.add(direita, BorderLayout.EAST);
        return cabecalho;
    }

    private JPanel criarPainelIndicadoresKpi() {
        JPanel grade = new JPanel(new GridLayout(1, 4, 14, 0));
        grade.setBackground(BG_APP);
        grade.setBorder(new EmptyBorder(12, 16, 4, 16));

        grade.add(criarCardKpi("TOTAL DE PRODUTOS", kpiTotalProdutos, COLOR_PRIMARY));
        grade.add(criarCardKpi("UNIDADES EM ESTOQUE", kpiTotalUnidades, COLOR_SUCCESS));
        grade.add(criarCardKpi("VALOR TOTAL EM ESTOQUE", kpiValorTotal, COLOR_INDIGO));
        grade.add(criarCardKpi("PRODUTOS EM ALERTA", kpiTotalAlertas, COLOR_DANGER));

        return grade;
    }

    private JPanel criarCardKpi(String rotulo, JLabel valorLabel, Color corDestaque) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, corDestaque),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR, 1),
                        new EmptyBorder(10, 14, 10, 14))));

        JLabel lblTitulo = new JLabel(rotulo);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTitulo.setForeground(TEXT_MUTED);

        valorLabel.setFont(FONT_KPI_VALUE);
        valorLabel.setForeground(TEXT_PRIMARY);

        card.add(lblTitulo, BorderLayout.NORTH);
        card.add(valorLabel, BorderLayout.CENTER);
        return card;
    }

    private void mostrarDialogoServidorOnline() {
        String porta = System.getenv().getOrDefault("WEB_PORT", "8081");
        String ipLocal = "localhost";
        try {
            ipLocal = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }
        String linkWifi = "http://" + ipLocal + ":" + porta + "/login";

        if (linkPublicoAtual != null) {
            copiarParaClipboard(linkPublicoAtual);
            JOptionPane.showMessageDialog(
                    null,
                    "Seu servidor online já está ativo!\n\n"
                            + "🌍 Link Público (qualquer lugar / 4G / Internet):\n"
                            + linkPublicoAtual + "\n\n"
                            + "📶 Link na mesma rede Wi-Fi:\n"
                            + linkWifi + "\n\n"
                            + "✔ O Link Público já foi copiado! Basta dar Ctrl+V no WhatsApp.",
                    "Servidor Online Ativo",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ssh",
                        "-o", "StrictHostKeyChecking=accept-new",
                        "-o", "ServerAliveInterval=30",
                        "-R", "80:localhost:" + porta,
                        "nokey@localhost.run");
                pb.redirectErrorStream(true);
                processoTunel = pb.start();

                Pattern padraoUrl = Pattern.compile("(https://[a-zA-Z0-9.-]+\\.lhr\\.life)");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(processoTunel.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        Matcher matcher = padraoUrl.matcher(linha);
                        if (matcher.find()) {
                            linkPublicoAtual = matcher.group(1) + "/login";
                            copiarParaClipboard(linkPublicoAtual);
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                    null,
                                    "🎉 Link Público da Internet gerado com sucesso!\n\n"
                                            + "🌍 Link para acessar de QUALQUER LUGAR:\n"
                                            + linkPublicoAtual + "\n\n"
                                            + "📶 Link na mesma rede Wi-Fi:\n"
                                            + linkWifi + "\n\n"
                                            + "✔ O link já foi COPIADO automaticamente! Basta colar (Ctrl+V) no WhatsApp.",
                                    "Link Online Copiado!",
                                    JOptionPane.INFORMATION_MESSAGE));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                copiarParaClipboard(linkWifi);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        null,
                        "Link da sua Rede / Wi-Fi copiado para a área de transferência:\n\n"
                                + linkWifi + "\n\n"
                                + "(Para acesso externo via internet, verifique se o OpenSSH e a conexão estão ativos).",
                        "Link da Rede Copiado",
                        JOptionPane.INFORMATION_MESSAGE));
            }
        }, "gerador-link-online");
        thread.setDaemon(true);
        thread.start();
    }

    private void copiarParaClipboard(String texto) {
        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(texto), null);
        } catch (Exception ignored) {
        }
    }

    private void abrirPainelWebNoNavegador() {
        String porta = System.getenv().getOrDefault("WEB_PORT", "8081");
        String url = "http://localhost:" + porta + "/login";
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        copiarParaClipboard(url);
        JOptionPane.showMessageDialog(
                null,
                "Link copiado! Abra no navegador:\n" + url,
                "Painel Web Online",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private Usuario autenticar() {
        while (true) {
            try {
                if (!usuarios.temUsuarios()) {
                    return cadastrarNovoUsuarioNaEntrada(true);
                }
            } catch (SQLException e) {
                mostrarErro("Nao foi possivel verificar os usuarios no banco: " + e.getMessage());
                return null;
            }

            JTextField email = criarCampoTexto("E-mail de acesso");
            email.setText("admin@estoque.com");
            JPasswordField senha = new JPasswordField("12345678", 20);
            estilizarInput(senha);

            JPanel painelLogin = new JPanel(new BorderLayout(0, 12));
            painelLogin.setBorder(new EmptyBorder(8, 8, 4, 8));

            JLabel boasVindas = new JLabel(
                    "<html><b>Bem-vindo ao Inventory Control!</b><br>"
                            + "<span style='color:#64748b;font-size:11px;'>Clique em <b>Entrar</b> ou em <b>Criar minha conta</b> se for seu primeiro acesso.</span></html>");
            boasVindas.setFont(FONT_REGULAR);
            painelLogin.add(boasVindas, BorderLayout.NORTH);

            JPanel formulario = new JPanel(new GridLayout(2, 2, 10, 10));
            formulario.add(criarRotulo("E-mail:"));
            formulario.add(email);
            formulario.add(criarRotulo("Senha:"));
            formulario.add(senha);
            painelLogin.add(formulario, BorderLayout.CENTER);

            Object[] opcoes = {"Entrar", "Criar minha conta (1º Acesso)", "Sair"};
            int escolha = JOptionPane.showOptionDialog(
                    null,
                    painelLogin,
                    "Entrar no Sistema",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    opcoes,
                    opcoes[0]);

            if (escolha == 1) {
                Usuario cadastrado = cadastrarNovoUsuarioNaEntrada(false);
                if (cadastrado != null) {
                    return cadastrado;
                }
                continue;
            }

            if (escolha != 0) {
                return null;
            }

            char[] senhaDigitada = senha.getPassword();
            try {
                return usuarios.login(email.getText(), new String(senhaDigitada));
            } catch (IllegalArgumentException e) {
                mostrarErro(e.getMessage());
            } catch (SQLException e) {
                mostrarErro("Erro ao acessar o banco de dados: " + e.getMessage());
                return null;
            } finally {
                Arrays.fill(senhaDigitada, '\0');
                senha.setText("");
            }
        }
    }

    private Usuario cadastrarNovoUsuarioNaEntrada(boolean primeiroAdmin) {
        JTextField nome = criarCampoTexto("Seu nome completo");
        JTextField email = criarCampoTexto("Seu e-mail");
        JPasswordField senha = new JPasswordField(20);
        estilizarInput(senha);
        JPasswordField confirmarSenha = new JPasswordField(20);
        estilizarInput(confirmarSenha);

        if (primeiroAdmin) {
            nome.setText("Administrador");
            email.setText("admin@estoque.com");
            senha.setText("12345678");
            confirmarSenha.setText("12345678");
        }

        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setBorder(new EmptyBorder(8, 8, 4, 8));
        JLabel dica = new JLabel(
                "<html><b>Criar nova conta (Primeiro acesso)</b><br>"
                        + "<span style='color:#64748b;font-size:11px;'>Preencha seus dados abaixo para criar seu acesso e entrar agora.</span></html>");
        dica.setFont(FONT_REGULAR);
        container.add(dica, BorderLayout.NORTH);

        JPanel formulario = new JPanel(new GridLayout(4, 2, 10, 10));
        formulario.add(criarRotulo("Seu nome:"));
        formulario.add(nome);
        formulario.add(criarRotulo("E-mail:"));
        formulario.add(email);
        formulario.add(criarRotulo("Senha (mín. 8 dígitos):"));
        formulario.add(senha);
        formulario.add(criarRotulo("Confirmar senha:"));
        formulario.add(confirmarSenha);
        container.add(formulario, BorderLayout.CENTER);

        while (true) {
            int resposta = JOptionPane.showConfirmDialog(
                    null,
                    container,
                    "Criar Minha Conta",
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

                Usuario novoUsuario = usuarios.registrarAutoCadastro(
                        nome.getText(),
                        email.getText(),
                        new String(senhaDigitada));

                JOptionPane.showMessageDialog(
                        null,
                        "Conta criada com sucesso! Você já está conectado.");
                return novoUsuario;

            } catch (IllegalArgumentException e) {
                mostrarErro(e.getMessage());
            } catch (SQLException e) {
                mostrarErro("Erro ao criar conta: " + e.getMessage());
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
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setBackground(BG_APP);
        painel.setBorder(new EmptyBorder(8, 4, 4, 4));

        JPanel cardTopo = criarCard();
        cardTopo.setLayout(new BorderLayout(0, 12));

        JLabel instrucao = new JLabel(
                "💡 Passo a passo: Preencha abaixo para cadastrar um produto novo, ou clique em um produto na tabela para editar ou somar/retirar unidades.");
        instrucao.setFont(FONT_REGULAR);
        instrucao.setForeground(TEXT_MUTED);
        cardTopo.add(instrucao, BorderLayout.NORTH);

        cardTopo.add(criarFormularioProduto(), BorderLayout.CENTER);

        JPanel rodapeCard = new JPanel(new BorderLayout(0, 8));
        rodapeCard.setOpaque(false);
        rodapeCard.add(criarBotoesProduto(), BorderLayout.NORTH);

        alertaEstoque.setFont(FONT_LABEL);
        alertaEstoque.setOpaque(true);
        alertaEstoque.setBorder(new EmptyBorder(8, 12, 8, 12));
        rodapeCard.add(alertaEstoque, BorderLayout.SOUTH);

        cardTopo.add(rodapeCard, BorderLayout.SOUTH);
        painel.add(cardTopo, BorderLayout.NORTH);

        JTable tabela = criarTabelaEstilizada(modeloProdutos);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<DefaultTableModel> ordenador = new TableRowSorter<>(modeloProdutos);
        tabela.setRowSorter(ordenador);

        tabela.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting() && tabela.getSelectedRow() >= 0) {
                int linha = tabela.convertRowIndexToModel(tabela.getSelectedRow());
                preencherCamposProduto(linha);
            }
        });

        buscaProduto.getDocument().addDocumentListener(new DocumentListener() {
            private void filtrar() {
                String termo = buscaProduto.getText().trim();
                if (termo.isEmpty()) {
                    ordenador.setRowFilter(null);
                } else {
                    ordenador.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(termo)));
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtrar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtrar();
            }
        });

        JPanel cardTabela = criarCard();
        cardTabela.setLayout(new BorderLayout(0, 10));

        JPanel barraTopoTabela = new JPanel(new BorderLayout(12, 0));
        barraTopoTabela.setOpaque(false);
        JLabel tituloTabela = new JLabel("Lista de Produtos Cadastrados (clique em um item para selecionar)");
        tituloTabela.setFont(FONT_SUBTITLE);
        tituloTabela.setForeground(TEXT_PRIMARY);
        barraTopoTabela.add(tituloTabela, BorderLayout.WEST);

        JPanel pesquisa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pesquisa.setOpaque(false);
        pesquisa.add(criarRotulo("🔍 Buscar:"));
        buscaProduto.setColumns(20);
        pesquisa.add(buscaProduto);
        JButton limparBusca = criarBotaoEstilizado("Limpar", COLOR_NEUTRAL, TEXT_PRIMARY);
        limparBusca.addActionListener(evento -> buscaProduto.setText(""));
        pesquisa.add(limparBusca);
        barraTopoTabela.add(pesquisa, BorderLayout.EAST);

        cardTabela.add(barraTopoTabela, BorderLayout.NORTH);
        cardTabela.add(criarScrollLimpo(tabela), BorderLayout.CENTER);

        painel.add(cardTabela, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarPainelHistorico() {
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setBackground(BG_APP);
        painel.setBorder(new EmptyBorder(8, 4, 4, 4));

        JTable tabela = criarTabelaEstilizada(modeloMovimentacoes);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel card = criarCard();
        card.setLayout(new BorderLayout(0, 10));

        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        JLabel titulo = new JLabel("Histórico Completo de Entradas e Saídas");
        titulo.setFont(FONT_SUBTITLE);
        titulo.setForeground(TEXT_PRIMARY);
        topo.add(titulo, BorderLayout.WEST);

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acoes.setOpaque(false);

        adicionarBotao(acoes, "↩ Estornar Selecionada", COLOR_WARNING, Color.WHITE,
                "Desfaz a movimentação selecionada na tabela e restaura o saldo", () -> {
                    int linha = tabela.getSelectedRow();
                    if (linha < 0) {
                        mostrarErro("Selecione uma movimentação na tabela para estornar.");
                        return;
                    }
                    long id = Long.parseLong(modeloMovimentacoes.getValueAt(linha, 0).toString());
                    executarAcao(
                            () -> estoque.estornarMovimentacao(id, usuarioAutenticado.getId()),
                            "Movimentação #" + id + " estornada com sucesso!");
                });

        adicionarBotao(acoes, "↻ Atualizar Histórico", COLOR_SECONDARY, Color.WHITE,
                "Recarrega a lista de movimentações", this::atualizarRelatorios);

        topo.add(acoes, BorderLayout.EAST);
        card.add(topo, BorderLayout.NORTH);
        card.add(criarScrollLimpo(tabela), BorderLayout.CENTER);

        painel.add(card, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarFormularioProduto() {
        JPanel formulario = new JPanel(new GridLayout(2, 5, 12, 4));
        formulario.setOpaque(false);

        formulario.add(criarRotulo("1. Código (Ex: MED001)"));
        formulario.add(criarRotulo("2. Medicamento / Produto"));
        formulario.add(criarRotulo("3. Preço Unitário (R$)"));
        formulario.add(criarRotulo("4. Quantidade (Caixas/Un.)"));
        formulario.add(criarRotulo("5. Estoque Mínimo (Alerta)"));

        formulario.add(codigo);
        formulario.add(nomeProduto);
        formulario.add(preco);
        formulario.add(quantidade);
        formulario.add(estoqueMinimo);

        return formulario;
    }

    private JPanel criarBotoesProduto() {
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botoes.setOpaque(false);

        adicionarBotao(botoes, "＋ Cadastrar Novo", COLOR_PRIMARY, Color.WHITE,
                "Salva um novo produto no sistema", this::cadastrarProduto);

        adicionarBotao(botoes, "✎ Salvar Edição", COLOR_SECONDARY, Color.WHITE,
                "Atualiza nome, preço e estoque mínimo do produto selecionado", this::atualizarProduto);

        adicionarBotao(botoes, "⬆ Entrada (+ Qtd)", COLOR_SUCCESS, Color.WHITE,
                "Soma a quantidade informada ao estoque do produto", this::adicionarEstoque);

        adicionarBotao(botoes, "⬇ Saída (- Qtd)", COLOR_WARNING, Color.WHITE,
                "Retira a quantidade informada do estoque do produto", this::retirarEstoque);

        adicionarBotao(botoes, "🗑 Excluir", COLOR_DANGER, Color.WHITE,
                "Remove o produto selecionado", this::removerProduto);

        adicionarBotao(botoes, "Limpar Campos", COLOR_NEUTRAL, TEXT_PRIMARY,
                "Limpa os campos acima para digitar outro item", this::limparCamposProduto);

        return botoes;
    }

    private JPanel criarPainelFornecedores() {
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setBackground(BG_APP);
        painel.setBorder(new EmptyBorder(8, 4, 4, 4));

        JPanel cardTopo = criarCard();
        cardTopo.setLayout(new BorderLayout(0, 12));

        JLabel instrucao = new JLabel(
                "💡 Cadastre aqui seus fornecedores. Apenas o campo Nome é obrigatório.");
        instrucao.setFont(FONT_REGULAR);
        instrucao.setForeground(TEXT_MUTED);
        cardTopo.add(instrucao, BorderLayout.NORTH);

        JPanel formulario = new JPanel(new GridLayout(2, 5, 12, 4));
        formulario.setOpaque(false);

        formulario.add(criarRotulo("Nome da Empresa *"));
        formulario.add(criarRotulo("CNPJ (opcional)"));
        formulario.add(criarRotulo("Telefone (opcional)"));
        formulario.add(criarRotulo("E-mail (opcional)"));
        formulario.add(criarRotulo("Endereço (opcional)"));

        formulario.add(nomeFornecedor);
        formulario.add(cnpjFornecedor);
        formulario.add(telefoneFornecedor);
        formulario.add(emailFornecedor);
        formulario.add(enderecoFornecedor);

        cardTopo.add(formulario, BorderLayout.CENTER);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botoes.setOpaque(false);

        adicionarBotao(botoes, "＋ Salvar Fornecedor", COLOR_PRIMARY, Color.WHITE,
                "Cadastra o fornecedor", this::cadastrarFornecedor);
        adicionarBotao(botoes, "Limpar Campos", COLOR_NEUTRAL, TEXT_PRIMARY,
                "Limpa os campos acima", this::limparCamposFornecedor);
        adicionarBotao(botoes, "↻ Atualizar Lista", COLOR_SECONDARY, Color.WHITE,
                "Recarrega a tabela de fornecedores", this::atualizarTabelaFornecedores);

        cardTopo.add(botoes, BorderLayout.SOUTH);
        painel.add(cardTopo, BorderLayout.NORTH);

        JPanel cardTabela = criarCard();
        cardTabela.setLayout(new BorderLayout(0, 8));
        JLabel tituloTabela = new JLabel("Fornecedores Cadastrados");
        tituloTabela.setFont(FONT_SUBTITLE);
        tituloTabela.setForeground(TEXT_PRIMARY);
        cardTabela.add(tituloTabela, BorderLayout.NORTH);
        cardTabela.add(criarScrollLimpo(criarTabelaEstilizada(modeloFornecedores)), BorderLayout.CENTER);

        painel.add(cardTabela, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarPainelRelatorios() {
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setBackground(BG_APP);
        painel.setBorder(new EmptyBorder(8, 4, 4, 4));

        JPanel tabelas = new JPanel(new GridLayout(1, 2, 14, 14));
        tabelas.setOpaque(false);

        JPanel cardBaixo = criarCard();
        cardBaixo.setLayout(new BorderLayout(0, 8));
        JLabel lblBaixo = new JLabel("⚠ Produtos Precisando de Reposição (Estoque Baixo)");
        lblBaixo.setFont(FONT_SUBTITLE);
        lblBaixo.setForeground(COLOR_DANGER);
        cardBaixo.add(lblBaixo, BorderLayout.NORTH);
        cardBaixo.add(criarScrollLimpo(criarTabelaEstilizada(modeloEstoqueBaixo)), BorderLayout.CENTER);

        JPanel cardMovimentados = criarCard();
        cardMovimentados.setLayout(new BorderLayout(0, 8));
        JLabel lblMovimentados = new JLabel("🔥 Produtos com Mais Saídas");
        lblMovimentados.setFont(FONT_SUBTITLE);
        lblMovimentados.setForeground(TEXT_PRIMARY);
        cardMovimentados.add(lblMovimentados, BorderLayout.NORTH);
        cardMovimentados.add(criarScrollLimpo(criarTabelaEstilizada(modeloMaisMovimentados)), BorderLayout.CENTER);

        tabelas.add(cardBaixo);
        tabelas.add(cardMovimentados);

        JPanel barraExportacao = criarCard();
        barraExportacao.setLayout(new BorderLayout());

        JLabel dicaExportacao = new JLabel("Gere arquivos prontos para impressão ou planilha:");
        dicaExportacao.setFont(FONT_REGULAR);
        dicaExportacao.setForeground(TEXT_MUTED);
        barraExportacao.add(dicaExportacao, BorderLayout.WEST);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botoes.setOpaque(false);

        adicionarBotao(botoes, "↻ Atualizar Dados", COLOR_SECONDARY, Color.WHITE,
                "Recalcula os relatórios agora", this::atualizarRelatorios);
        adicionarBotao(botoes, "📄 Baixar Relatório PDF", COLOR_DANGER, Color.WHITE,
                "Salva um arquivo PDF com o relatório do estoque", this::exportarPdf);
        adicionarBotao(botoes, "📊 Baixar Planilha Excel", COLOR_SUCCESS, Color.WHITE,
                "Salva uma planilha Excel (.xlsx) do estoque", this::exportarExcel);

        barraExportacao.add(botoes, BorderLayout.EAST);

        painel.add(tabelas, BorderLayout.CENTER);
        painel.add(barraExportacao, BorderLayout.SOUTH);
        return painel;
    }

    private JPanel criarCard() {
        JPanel card = new JPanel();
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(14, 16, 14, 16)));
        return card;
    }

    private static JTextField criarCampoTexto(String dica) {
        JTextField campo = new JTextField();
        campo.setToolTipText(dica);
        estilizarInput(campo);
        return campo;
    }

    private static void estilizarInput(JComponent campo) {
        campo.setFont(FONT_REGULAR);
        campo.setForeground(TEXT_PRIMARY);
        campo.setBackground(Color.WHITE);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private JLabel criarRotulo(String texto) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(FONT_LABEL);
        rotulo.setForeground(TEXT_PRIMARY);
        return rotulo;
    }

    private JButton criarBotaoEstilizado(String texto, Color fundo, Color corTexto) {
        JButton botao = new JButton(texto);
        botao.setFont(FONT_BUTTON);
        botao.setBackground(fundo);
        botao.setForeground(corTexto);
        botao.setFocusPainted(false);
        botao.setBorderPainted(false);
        botao.setOpaque(true);
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        botao.setBorder(new EmptyBorder(8, 14, 8, 14));
        return botao;
    }

    private JTable criarTabelaEstilizada(DefaultTableModel modelo) {
        JTable tabela = new JTable(modelo);
        tabela.setFont(FONT_REGULAR);
        tabela.setForeground(TEXT_PRIMARY);
        tabela.setRowHeight(32);
        tabela.setShowVerticalLines(false);
        tabela.setGridColor(BORDER_COLOR);
        tabela.setSelectionBackground(new Color(219, 234, 254));
        tabela.setSelectionForeground(new Color(30, 58, 138));
        tabela.setFillsViewportHeight(true);

        JTableHeader header = tabela.getTableHeader();
        header.setFont(FONT_LABEL);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(TEXT_PRIMARY);
        header.setPreferredSize(new Dimension(header.getWidth(), 34));

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                    c.setForeground(TEXT_PRIMARY);
                }
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return c;
            }
        };
        for (int i = 0; i < tabela.getColumnCount(); i++) {
            tabela.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        return tabela;
    }

    private JScrollPane criarScrollLimpo(JTable tabela) {
        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    private void exportarPdf() {
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Salvar relatorio PDF");
        seletor.setSelectedFile(new File("relatorio-estoque.pdf"));
        seletor.setFileFilter(new FileNameExtensionFilter("Arquivo PDF (*.pdf)", "pdf"));

        if (seletor.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File arquivo = seletor.getSelectedFile();
        if (!arquivo.getName().toLowerCase().endsWith(".pdf")) {
            arquivo = new File(arquivo.getAbsolutePath() + ".pdf");
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
            relatorioExportacao.exportarPdf(arquivo.toPath());
            JOptionPane.showMessageDialog(
                    null,
                    "PDF exportado com sucesso!\n" + arquivo.getAbsolutePath());
        } catch (SQLException | IOException e) {
            mostrarErro("Erro ao exportar PDF: " + e.getMessage());
        }
    }

    private void exportarExcel() {
        JFileChooser seletor = new JFileChooser();
        seletor.setDialogTitle("Salvar relatorio Excel");
        seletor.setSelectedFile(new File("relatorio-estoque.xlsx"));
        seletor.setFileFilter(new FileNameExtensionFilter("Arquivo Excel (*.xlsx)", "xlsx"));

        if (seletor.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File arquivo = seletor.getSelectedFile();
        if (!arquivo.getName().toLowerCase().endsWith(".xlsx")) {
            arquivo = new File(arquivo.getAbsolutePath() + ".xlsx");
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
            relatorioExportacao.exportarExcel(arquivo.toPath());
            JOptionPane.showMessageDialog(
                    null,
                    "Excel exportado com sucesso!\n" + arquivo.getAbsolutePath());
        } catch (SQLException | IOException e) {
            mostrarErro("Erro ao exportar Excel: " + e.getMessage());
        }
    }

    private void adicionarBotao(
            JPanel painel,
            String texto,
            Color fundo,
            Color corTexto,
            String dica,
            Runnable acao
    ) {
        JButton botao = criarBotaoEstilizado(texto, fundo, corTexto);
        botao.setToolTipText(dica);
        botao.addActionListener(evento -> acao.run());
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
                "Produto cadastrado com sucesso!");
    }

    private void atualizarProduto() {
        executarAcao(
                () -> estoque.atualizar(
                        lerCodigo(),
                        nomeProduto.getText(),
                        lerPreco(),
                        lerEstoqueMinimo()),
                "Produto atualizado com sucesso!");
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
                "Entrada de estoque registrada!");
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
                "Saída de estoque registrada!");
    }

    private void removerProduto() {
        String codigoProduto = lerCodigo();
        if (codigoProduto.isEmpty()) {
            mostrarErro("Clique em um produto na tabela ou digite o código que deseja excluir.");
            return;
        }

        int resposta = JOptionPane.showConfirmDialog(
                null,
                "Deseja realmente excluir o produto " + codigoProduto + "?",
                "Confirmar exclusão",
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
            JOptionPane.showMessageDialog(null, "Fornecedor cadastrado com sucesso!");
        } catch (IllegalArgumentException e) {
            mostrarErro(e.getMessage());
        } catch (SQLException e) {
            mostrarErro("Erro ao cadastrar fornecedor: " + e.getMessage());
        }
    }

    private void cadastrarFuncionario(Usuario administrador) {
        JTextField nome = criarCampoTexto("Nome do funcionário");
        JTextField email = criarCampoTexto("E-mail do funcionário");
        JPasswordField senha = new JPasswordField(20);
        estilizarInput(senha);
        JPasswordField confirmarSenha = new JPasswordField(20);
        estilizarInput(confirmarSenha);

        JPanel formulario = new JPanel(new GridLayout(4, 2, 10, 10));
        formulario.setBorder(new EmptyBorder(8, 8, 8, 8));
        formulario.add(criarRotulo("Nome:"));
        formulario.add(nome);
        formulario.add(criarRotulo("E-mail:"));
        formulario.add(email);
        formulario.add(criarRotulo("Senha (mín. 8 dígitos):"));
        formulario.add(senha);
        formulario.add(criarRotulo("Confirmar senha:"));
        formulario.add(confirmarSenha);

        int resposta = JOptionPane.showConfirmDialog(
                null,
                formulario,
                "Cadastrar Novo Usuário",
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

            JOptionPane.showMessageDialog(null, "Usuário cadastrado com sucesso!");
        } catch (IllegalArgumentException e) {
            mostrarErro(e.getMessage());
        } catch (SQLException e) {
            mostrarErro("Erro ao cadastrar usuario: " + e.getMessage());
        } finally {
            Arrays.fill(senhaDigitada, '\0');
            Arrays.fill(confirmacao, '\0');
            senha.setText("");
            confirmarSenha.setText("");
        }
    }

    private boolean executarAcao(Acao acao, String mensagem) {
        try {
            acao.executar();
            atualizarTabelaProdutos();
            atualizarRelatorios();
            JOptionPane.showMessageDialog(null, mensagem);
            return true;
        } catch (NumberFormatException e) {
            mostrarErro("Preencha Preço e Quantidade apenas com números válidos (ex: 19.90 e 10).");
        } catch (IllegalArgumentException | ArithmeticException e) {
            mostrarErro(e.getMessage());
        } catch (SQLException e) {
            mostrarErro("Erro ao acessar o banco de dados: " + e.getMessage());
        }
        return false;
    }

    private void atualizarTabelaProdutos() throws SQLException {
        modeloProdutos.setRowCount(0);
        var listaProdutos = estoque.listar();

        long totalUnidades = 0;
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (Produto produto : listaProdutos) {
            modeloProdutos.addRow(
                    new Object[]{
                            produto.getCodigo(),
                            produto.getNome(),
                            formatarMoeda(produto.getPreco()),
                            produto.getQuantidade(),
                            produto.getEstoqueMinimo()
                    });
            totalUnidades += produto.getQuantidade();
            valorTotal = valorTotal.add(produto.getPreco().multiply(BigDecimal.valueOf(produto.getQuantidade())));
        }

        var alertas = estoque.listarAlertasEstoqueMinimo();

        kpiTotalProdutos.setText(String.valueOf(listaProdutos.size()));
        kpiTotalUnidades.setText(String.valueOf(totalUnidades));
        kpiValorTotal.setText(NumberFormat.getCurrencyInstance(LOCALE_BR).format(valorTotal));
        kpiTotalAlertas.setText(String.valueOf(alertas.size()));
        kpiTotalAlertas.setForeground(alertas.isEmpty() ? COLOR_SUCCESS : COLOR_DANGER);

        if (alertas.isEmpty()) {
            alertaEstoque.setBackground(new Color(220, 252, 231));
            alertaEstoque.setForeground(new Color(22, 101, 52));
            alertaEstoque.setText("✔ Tudo certo! Todos os produtos estão acima do estoque mínimo.");
        } else {
            alertaEstoque.setBackground(new Color(254, 226, 226));
            alertaEstoque.setForeground(new Color(153, 27, 27));
            alertaEstoque.setText(
                    "⚠ Atenção — Estoque baixo para: "
                            + String.join(", ", alertas.stream().map(Produto::getNome).toList()));
        }
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "0.00";
        }
        BigDecimal limpo = valor.stripTrailingZeros();
        int escala = Math.max(2, limpo.scale());
        return limpo.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    private void atualizarTabelaFornecedores() {
        try {
            modeloFornecedores.setRowCount(0);
            for (Fornecedor fornecedor : fornecedores.listar()) {
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
            mostrarErro("Erro ao carregar fornecedores: " + e.getMessage());
        }
    }

    private void atualizarRelatorios() {
        try {
            modeloEstoqueBaixo.setRowCount(0);
            modeloMaisMovimentados.setRowCount(0);
            modeloMovimentacoes.setRowCount(0);

            for (Produto produto : estoque.listar()) {
                if (produto.getQuantidade() <= produto.getEstoqueMinimo()) {
                    modeloEstoqueBaixo.addRow(
                            new Object[]{
                                    produto.getCodigo(),
                                    produto.getNome(),
                                    produto.getQuantidade()
                            });
                }
            }

            for (ProdutoMovimentado produto : movimentacaoRepository.listarMaisMovimentados()) {
                modeloMaisMovimentados.addRow(
                        new Object[]{
                                produto.getCodigo(),
                                produto.getNome(),
                                produto.getQuantidadeMovimentada()
                        });
            }

            for (MovimentacaoEstoque mov : estoque.listarHistorico(null, null, null, null)) {
                String sinal = mov.direcao().name().equals("ENTRADA") ? "+" : "-";
                String status = mov.estornadaPorId() != null
                        ? "Estornada (#" + mov.estornadaPorId() + ")"
                        : (mov.estornoDeId() != null ? "Estorno de #" + mov.estornoDeId() : "Ativa");
                modeloMovimentacoes.addRow(new Object[]{
                        mov.id(),
                        mov.data().format(FORMATO_DATA),
                        mov.codigoProduto(),
                        mov.tipo().name(),
                        mov.motivo().name(),
                        sinal + mov.quantidade(),
                        mov.saldoAntes() + " → " + mov.saldoDepois(),
                        status
                });
            }
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar relatorios: " + e.getMessage());
        }
    }

    private void preencherCamposProduto(int linha) {
        codigo.setText(modeloProdutos.getValueAt(linha, 0).toString());
        nomeProduto.setText(modeloProdutos.getValueAt(linha, 1).toString());
        preco.setText(modeloProdutos.getValueAt(linha, 2).toString());
        quantidade.setText(modeloProdutos.getValueAt(linha, 3).toString());
        estoqueMinimo.setText(modeloProdutos.getValueAt(linha, 4).toString());
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

    private int lerEstoqueMinimo() {
        String texto = estoqueMinimo.getText().trim();
        return texto.isEmpty() ? 0 : Integer.parseInt(texto);
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
        return perfil == PerfilAcesso.ADMIN ? "Administrador" : "Funcionário";
    }

    private String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(
                null,
                mensagem,
                "Atenção",
                JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws SQLException;
    }
}
