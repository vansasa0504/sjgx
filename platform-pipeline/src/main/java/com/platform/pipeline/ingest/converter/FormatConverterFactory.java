package com.platform.pipeline.ingest.converter;

import com.platform.pipeline.ingest.FormatConverter;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FormatConverterFactory {
    private final Map<String, FormatConverter> converters;

    public FormatConverterFactory(Collection<FormatConverter> converters) {
        this.converters = converters.stream().collect(Collectors.toMap(c -> c.format().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public FormatConverter get(String format) {
        FormatConverter converter = converters.get(format.toUpperCase(Locale.ROOT));
        if (converter == null) {
            throw new IllegalArgumentException("unsupported format: " + format);
        }
        return converter;
    }
}
