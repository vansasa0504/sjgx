package com.platform.pipeline.ingest.converter;

public class ExcelConverter extends CsvConverter {
    @Override
    public String format() { return "EXCEL"; }
}
