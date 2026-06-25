package com.platform.billing.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ReportGenerator {
    public GeneratedReport generate(ReportType type, List<String> rows, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            Path path = outputDir.resolve(type.name().toLowerCase() + "-report.xlsx");
            try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
                Sheet sheet = workbook.createSheet(type.name());
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("report_type");
                header.createCell(1).setCellValue("item");
                for (int i = 0; i < rows.size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    row.createCell(0).setCellValue(type.name());
                    row.createCell(1).setCellValue(rows.get(i));
                }
                workbook.write(output);
            }
            return new GeneratedReport(type, path);
        } catch (IOException ex) {
            throw new IllegalStateException("report generate failed", ex);
        }
    }
}