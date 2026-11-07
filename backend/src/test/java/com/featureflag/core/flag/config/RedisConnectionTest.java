
package com.featureflag.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldWriteAndReadValueFromRedis() {

        String key = "test:redis:connection";
        String value = "Redis is connected!";

        redisTemplate.opsForValue().set(key, value);

        String result = redisTemplate.opsForValue().get(key);

        assertEquals(value, result);

        redisTemplate.delete(key);
    }
}
