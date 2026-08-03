package com.qgyun.hltgq.hltgqsite.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Jackson 全局配置 — BigDecimal 序列化时保留尾零
 * <p>
 * 默认行为会丢弃 scale 带来的尾零（如 23.10 → 23.1），
 * 导致与 TRUNC(..., 2) 的语义不一致。
 * 此处用 toPlainString() 直接输出原始字符串表示，
 * 确保 scale 信息完整传递到前端。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalCustomizer() {
        return builder -> builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                // toPlainString 会保留 BigDecimal 的 scale 尾零
                // 如：BigDecimal("23.10") → "23.10"，BigDecimal("0.50") → "0.50"
                gen.writeRawValue(value.toPlainString());
            }

            @Override
            public Class<BigDecimal> handledType() {
                return BigDecimal.class;
            }
        });
    }
}
