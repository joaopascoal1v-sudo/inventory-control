
package com.estoque.service;

import com.estoque.entities.Produto;
import com.estoque.entities.ProdutoMovimentado;
import com.estoque.repository.MovimentacaoRepositoryJdbc;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class RelatorioExportacaoService {

    private final EstoqueService estoque;
    private final MovimentacaoRepositoryJdbc movimentacaoRepository;

    public RelatorioExportacaoService(EstoqueService estoque) {
        this.estoque = estoque;
        this.movimentacaoRepository = new MovimentacaoRepositoryJdbc();
    }

    // =========================
    // EXPORTAR EXCEL
    // =========================

    public void exportarExcel(Path destino) throws SQLException, IOException {

        List<Produto> produtos = estoque.listar();

        List<ProdutoMovimentado> movimentados =
                movimentacaoRepository.listarMaisMovimentados();

        try (Workbook workbook = new XSSFWorkbook()) {

            // Aba de estoque baixo
            Sheet estoqueBaixo = workbook.createSheet("Estoque baixo");

            Row cabecalho = estoqueBaixo.createRow(0);
            cabecalho.createCell(0).setCellValue("Código");
            cabecalho.createCell(1).setCellValue("Produto");
            cabecalho.createCell(2).setCellValue("Estoque");

            int linha = 1;

            for (Produto produto : produtos) {

                if (produto.getQuantidade() <= 5) {

                    Row row = estoqueBaixo.createRow(linha++);

                    row.createCell(0).setCellValue(produto.getCodigo());
                    row.createCell(1).setCellValue(produto.getNome());
                    row.createCell(2).setCellValue(produto.getQuantidade());
                }
            }

            // Aba de produtos mais movimentados
            Sheet maisMovimentados =
                    workbook.createSheet("Mais movimentados");

            Row cab = maisMovimentados.createRow(0);

            cab.createCell(0).setCellValue("Código");
            cab.createCell(1).setCellValue("Produto");
            cab.createCell(2).setCellValue("Quantidade movimentada");

            linha = 1;

            for (ProdutoMovimentado produto : movimentados) {

                Row row = maisMovimentados.createRow(linha++);

                row.createCell(0).setCellValue(produto.getCodigo());
                row.createCell(1).setCellValue(produto.getNome());
                row.createCell(2).setCellValue(
                        produto.getQuantidadeMovimentada()
                );
            }

            // Ajustar largura das colunas
            for (int i = 0; i < 3; i++) {
                estoqueBaixo.autoSizeColumn(i);
                maisMovimentados.autoSizeColumn(i);
            }

            // Salvar arquivo
            try (OutputStream out = Files.newOutputStream(destino)) {
                workbook.write(out);
            }
        }
    }

    // =========================
    // EXPORTAR PDF
    // =========================

    public void exportarPdf(Path destino) throws SQLException, IOException {

        List<Produto> produtos = estoque.listar();

        List<ProdutoMovimentado> movimentados =
                movimentacaoRepository.listarMaisMovimentados();

        try (PDDocument documento = new PDDocument()) {

            PDPage pagina = new PDPage(PDRectangle.A4);

            documento.addPage(pagina);

            try (PDPageContentStream conteudo =
                         new PDPageContentStream(documento, pagina)) {

                conteudo.beginText();

                // Título
                conteudo.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA_BOLD
                        ),
                        16
                );

                conteudo.newLineAtOffset(50, 780);

                conteudo.showText("RELATORIO DE ESTOQUE");

                // Subtítulo
                conteudo.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA
                        ),
                        10
                );

                conteudo.newLineAtOffset(0, -30);

                conteudo.showText("PRODUTOS COM ESTOQUE BAIXO");

                conteudo.newLineAtOffset(0, -20);

                // Produtos com estoque baixo
                for (Produto produto : produtos) {

                    if (produto.getQuantidade() <= 5) {

                        escreverLinha(
                                conteudo,
                                produto.getNome()
                                        + " - Estoque: "
                                        + produto.getQuantidade()
                        );
                    }
                }

                // Mais movimentados
                conteudo.newLineAtOffset(0, -25);

                conteudo.showText("PRODUTOS MAIS MOVIMENTADOS");

                conteudo.newLineAtOffset(0, -20);

                for (ProdutoMovimentado produto : movimentados) {

                    escreverLinha(
                            conteudo,
                            produto.getNome()
                                    + " - Quantidade: "
                                    + produto.getQuantidadeMovimentada()
                    );
                }

                conteudo.endText();
            }

            // Salvar PDF
            documento.save(destino.toFile());
        }
    }

    // =========================
    // ESCREVER LINHAS NO PDF
    // =========================

    private void escreverLinha(
            PDPageContentStream conteudo,
            String texto
    ) throws IOException {

        conteudo.showText(texto);

        conteudo.newLineAtOffset(0, -15);
    }
}
