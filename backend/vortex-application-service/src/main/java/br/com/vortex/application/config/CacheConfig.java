package br.com.vortex.application.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Slf4j
@ConditionalOnProperty(
    value = "spring.cache.type",
    havingValue = "redis",
    matchIfMissing = false
)
public class CacheConfig {

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
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createJsonRedisSerializer()))
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
        template.setValueSerializer(createJsonRedisSerializer());
        template.setHashValueSerializer(createJsonRedisSerializer());
        
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        log.info("Redis template configured with JSON serialization");
        return template;
    }

    private Jackson2JsonRedisSerializer<Object> createJsonRedisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        om.registerModule(new JavaTimeModule());
        
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(om);
        
        return jackson2JsonRedisSerializer;
    }
}