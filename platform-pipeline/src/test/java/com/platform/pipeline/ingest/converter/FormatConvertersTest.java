package com.platform.pipeline.ingest.converter;

import com.alibaba.excel.EasyExcel;
import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatConvertersTest {
    @Test
    void convertsXmlCsvAndExcelPayloads() {
        XmlConverter xml = new XmlConverter();
        CsvConverter csv = new CsvConverter();
        ExcelConverter excel = new ExcelConverter();
        FormatConverterFactory factory = new FormatConverterFactory(List.of(xml, csv, excel));

        assertEquals("001", xml.convert("<root><code>001</code><name>alpha</name></root>").get(0).get("code"));
        assertEquals("Smith, John", csv.convert("id,name\n1,\"Smith, John\"").get(0).get("name"));
        assertEquals("gamma", excel.convert(excelPayload()).get(0).get("name"));
        assertEquals("CSV", factory.get("csv").format());
    }

    @Test
    void rejectsBrokenFormatPayloads() {
        assertThrows(BusinessException.class, () -> new XmlConverter().convert("<root>"));
        assertThrows(BusinessException.class, () -> new CsvConverter().convert("id,name\n1"));
        String invalidExcel = Base64.getEncoder().encodeToString("not an xlsx".getBytes(StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> new ExcelConverter().convert(invalidExcel));
    }

    private static String excelPayload() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output)
                .head(List.of(List.of("id"), List.of("name")))
                .sheet("data")
                .doWrite(List.of(List.of("1", "gamma")));
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }
}

