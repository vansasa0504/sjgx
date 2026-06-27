package com.platform.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域对象（Partner/Consumer/IngestTask 等）使用 {@code id()} 风格访问器而非标准 {@code getX()}，
 * Jackson 默认无法发现属性。此处开启字段可见性，使 Jackson 直接序列化字段，兼容领域对象与 record。
 */
@Configuration
public class JacksonConfiguration {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer fieldAccessCustomizer() {
        return builder -> builder.visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }
}
