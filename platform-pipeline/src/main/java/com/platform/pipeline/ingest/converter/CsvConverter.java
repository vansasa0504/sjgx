package com.platform.pipeline.ingest.converter;

import com.platform.common.exception.BusinessException;
import com.platform.pipeline.ingest.FormatConverter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvConverter implements FormatConverter {
    @Override
    public String format() { return "CSV"; }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(rawPayload))) {
            return parser.stream().map(this::toMap).toList();
        } catch (Exception ex) {
            throw new BusinessException("INGEST-400", "invalid CSV payload");
        }
    }

    private Map<String, String> toMap(CSVRecord record) {
        if (!record.isConsistent()) {
            throw new BusinessException("INGEST-400", "invalid CSV payload");
        }
        Map<String, String> row = new LinkedHashMap<>();
        record.toMap().forEach(row::put);
        return row;
    }
}
