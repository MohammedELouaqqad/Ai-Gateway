package com.backend.Ai_Gateway.config;

import com.backend.Ai_Gateway.services.guardrails.BudgetStore;
import com.backend.Ai_Gateway.services.guardrails.RateLimitStore;
import com.backend.Ai_Gateway.services.guardrails.redis.RedisBudgetStore;
import com.backend.Ai_Gateway.services.guardrails.redis.RedisRateLimitStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisGuardConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory(RedisGuardProperties props) {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration();
        conf.setHostName(props.getHost());
        conf.setPort(props.getPort());
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            conf.setPassword(RedisPassword.of(props.getPassword()));
        }
        return new LettuceConnectionFactory(conf);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    RateLimitStore redisRateLimitStore(StringRedisTemplate template, MeterRegistry meterRegistry) {
        return new RedisRateLimitStore(template, meterRegistry, loadScript("redis/rpm_peek.lua", Long.class),
                loadScript("redis/rpm_try_acquire.lua", java.util.List.class),
                loadScript("redis/rpm_release.lua", Long.class),
                loadScript("redis/rpd_try_acquire.lua", java.util.List.class));
    }

    @Bean
    BudgetStore redisBudgetStore(StringRedisTemplate template, MeterRegistry meterRegistry) {
        return new RedisBudgetStore(template, meterRegistry,
                loadScript("redis/budget_reserve.lua", java.util.List.class),
                loadScript("redis/budget_adjust.lua", Long.class));
    }

    private static <T> DefaultRedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(resultType);
        return script;
    }
}
