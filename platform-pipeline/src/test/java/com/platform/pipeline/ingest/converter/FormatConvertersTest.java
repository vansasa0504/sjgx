package com.platform.pipeline.ingest.converter;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

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
        assertEquals("beta", csv.convert("id,name\n1,beta").get(0).get("name"));
        assertEquals("gamma", excel.convert("id,name\n1,gamma").get(0).get("name"));
        assertEquals("CSV", factory.get("csv").format());
    }

    @Test
    void rejectsBrokenFormatPayloads() {
        assertThrows(BusinessException.class, () -> new XmlConverter().convert("<root>"));
        assertThrows(BusinessException.class, () -> new CsvConverter().convert("id,name\n1"));
    }
}
