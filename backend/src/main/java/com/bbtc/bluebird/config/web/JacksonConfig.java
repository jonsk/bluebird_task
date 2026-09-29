package com.bbtc.bluebird.config.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置（02 §1.8）：ISO8601 + 时区（{@code write-dates-as-timestamps=false}），
 * 时间按 {@code +08:00} 偏移输出（契约统一 2026-10-01T18:00:00+08:00）。
 */
@Configuration
public class JacksonConfig {

    private static final ZoneId ZONE = ZoneId.of("GMT+8");

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new InstantSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    /** Instant → 带 +08:00 偏移的 ISO8601。 */
    static final class InstantSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(OffsetDateTime.ofInstant(value, ZONE)));
        }
    }
}
