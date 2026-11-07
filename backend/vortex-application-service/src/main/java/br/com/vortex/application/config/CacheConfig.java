package br.com.vortex.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableCaching
@Slf4j
@ConditionalOnProperty(
    value = "spring.cache.type",
    havingValue = "redis",
    matchIfMissing = false
)
public class CacheConfig {

    /**
     * Prefixo das chaves do cache. Na migração para o Jackson 3 o JSON gravado mudou (o tipo vai em
     * "@class" em vez do array [classe, valor] do serializer antigo); com o prefixo novo as entradas
     * antigas só expiram pelo TTL, em vez de falharem na leitura depois do deploy.
     */
    static final String CACHE_KEY_PREFIX = "v2:";

    /** Tipos escalares do JDK aceitos com informação de tipo nos valores do cache. */
    private static final Set<Class<?>> JDK_VALUE_TYPES = Set.of(
            String.class, Boolean.class, Character.class, Byte.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class, BigDecimal.class, BigInteger.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.cache.redis.time-to-live:600000}")
    private long defaultTtl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        return new JedisConnectionFactory(config);
    }

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(defaultTtl))
                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Cache específico para tipos de produto (dados mais estáticos)
        cacheConfigurations.put("produto-types", defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        // Cache para produtos (moderadamente dinâmico)
        cacheConfigurations.put("produtos", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        // Cache para validação JWT (vida curta mas alta performance)
        cacheConfigurations.put("jwt-validation", defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("jwt-claims", defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        // Cache para usuários (dados moderadamente estáticos)
        cacheConfigurations.put("usuarios", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonRedisSerializer());
        template.setHashValueSerializer(jsonRedisSerializer());

        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        log.info("Redis template configured with JSON serialization");
        return template;
    }

    /**
     * Serializer JSON (Jackson 3) dos valores do cache, com informação de tipo para os valores
     * polimórficos ({@code Object}) que o Spring Cache grava.
     */
    static RedisSerializer<Object> jsonRedisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(cacheTypeValidator())
                .build();
    }

    /**
     * Lista fechada dos tipos que o JSON do cache pode instanciar: os DTOs da aplicação, coleções e
     * mapas do JDK (inclusive os de {@code List.of()}/{@code Stream.toList()}), escalares e java.time.
     * Substitui o {@code LaissezFaireSubTypeValidator}, que instanciava qualquer classe indicada no
     * JSON lido do Redis.
     */
    static PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("br.com.vortex.application.dto.")
                .allowIfSubType((context, type) -> type.getName().startsWith("java.util.")
                        && (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)))
                .allowIfSubType((context, type) -> JDK_VALUE_TYPES.contains(type))
                .allowIfSubType("java.time.")
                .build();
    }
}
