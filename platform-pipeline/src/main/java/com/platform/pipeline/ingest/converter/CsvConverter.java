package com.platform.pipeline.ingest.converter;

import com.platform.common.exception.BusinessException;
import com.platform.pipeline.ingest.FormatConverter;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvConverter implements FormatConverter {
    @Override
    public String format() { return "CSV"; }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        try (BufferedReader reader = new BufferedReader(new StringReader(rawPayload))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return List.of();
            }
            String[] headers = headerLine.split(",", -1);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length != headers.length) {
                    throw new BusinessException("INGEST-400", "broken CSV row");
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], values[i]);
                }
                rows.add(row);
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("INGEST-400", "invalid CSV payload");
        }
    }
}
