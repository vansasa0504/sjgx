package com.platform.pipeline.ingest.converter;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.platform.common.exception.BusinessException;
import com.platform.pipeline.ingest.FormatConverter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelConverter implements FormatConverter {
    @Override
    public String format() { return "EXCEL"; }

    @Override
    public List<Map<String, String>> convert(String rawPayload) {
        try {
            byte[] bytes = Base64.getDecoder().decode(rawPayload);
            List<Map<String, String>> rows = new ArrayList<>();
            EasyExcel.read(new ByteArrayInputStream(bytes), new AnalysisEventListener<Map<Integer, String>>() {
                private Map<Integer, String> headers = Map.of();

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    headers = new LinkedHashMap<>(headMap);
                }

                @Override
                public void invoke(Map<Integer, String> row, AnalysisContext context) {
                    Map<String, String> converted = new LinkedHashMap<>();
                    row.forEach((index, value) -> converted.put(headers.getOrDefault(index, "column_" + index), value));
                    rows.add(converted);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                }
            }).sheet().doRead();
            if (rows.isEmpty()) {
                throw new BusinessException("INGEST-400", "invalid EXCEL payload");
            }
            return rows;
        } catch (Exception ex) {
            throw new BusinessException("INGEST-400", "invalid EXCEL payload");
        }
    }
}

