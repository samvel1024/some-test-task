package com.project.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@SpringBootApplication
@Configuration
@Slf4j
public class RedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }


    @Configuration
    public static class ContextConfiguration {

        @Value("${redis.host:localhost}")
        String redisHost;

        @Value("${redis.port:6379}")
        int redisPort;


        @Bean
        public JedisConnectionFactory jedisConnectionFactory() {
            val rsc = new RedisStandaloneConfiguration(redisHost, redisPort);
            return new JedisConnectionFactory(rsc);
        }

        @Bean
        public RedisTemplate<String, Record> redisTemplate(@Autowired JedisConnectionFactory jcf, @Autowired ObjectMapper mapper) {
            val jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Record.class);
            jackson2JsonRedisSerializer.setObjectMapper(mapper);

            val rt = new RedisTemplate<String, Record>();
            rt.setConnectionFactory(jcf);
            rt.setKeySerializer(new StringRedisSerializer());
            rt.setValueSerializer(jackson2JsonRedisSerializer);
            rt.setHashKeySerializer(new StringRedisSerializer());
            rt.setHashValueSerializer(jackson2JsonRedisSerializer);
            rt.afterPropertiesSet();
            return rt;
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Record implements Serializable {
        UUID id;
        LocalDateTime created;
        String content;
    }


    public interface RecordService {
        void save(Record record);

        Optional<Record> getLast();

        Collection<Record> getRange(LocalDateTime from, LocalDateTime to, long limit);

        Collection<Record> getRange(LocalDateTime from, LocalDateTime to);
    }

    @Service
    public static class RecordServiceImpl implements RecordService {

        final static ZoneId ZONE_ID = ZoneId.systemDefault();

        static long toTimestamp(LocalDateTime date) {
            return date.atZone(ZONE_ID).toInstant().toEpochMilli();
        }


        @Autowired
        RedisTemplate<String, Record> redis;

        @Value("${redis.records-name:records}")
        String recordsName;

        @Override
        public void save(Record record) {
            Assert.noNullElements(new Object[]{record, record.content, record.created, record.id},
                    "Null value in record");
            val timestamp = toTimestamp(record.created);
            val added = redis.opsForZSet().add(recordsName, record, timestamp);
            Assert.isTrue(added, () -> record + " is already added");
            log.debug("Created record {}", record);
        }

        @Override
        public Optional<Record> getLast() {
            log.debug("");


            return Objects.requireNonNull(redis
                    .opsForZSet()
                    .reverseRangeByScore(recordsName, 0, Long.MAX_VALUE, 0, 1))
                    .stream()
                    .peek(rec -> log.debug("GetLast for {}", rec))
                    .findFirst();
        }

        @Override
        public Collection<Record> getRange(LocalDateTime from, LocalDateTime to, long limit) {
            val low = toTimestamp(from);
            val up = toTimestamp(to);
            Assert.isTrue(low <= up, "From is larger than to");
            val recs = new ArrayList<>(Objects.requireNonNull(redis.opsForZSet().rangeByScore(recordsName, low, up)));
            log.debug("GetRange from {} with window {} returns {} results", low, up - low, recs.size());
            return recs;
        }

        @Override
        public Collection<Record> getRange(LocalDateTime from, LocalDateTime to) {
            return getRange(from, to, Long.MAX_VALUE);
        }

    }


    @RestController
    @RequestMapping("/")
    public static class WebApiController {

        static final int MAX_PAGE_SIZE = 1000;

        @Autowired
        RecordService recordService;

        @Data
        public static class CreateRecordReq {
            String content;
        }

        @RequestMapping(method = POST, path = "publish")
        public ResponseEntity<Record> publish(@RequestBody CreateRecordReq dto) {
            Assert.notNull(dto.content, "Content cannot be null");
            val rec = new Record(UUID.randomUUID(), LocalDateTime.now(), dto.content);
            recordService.save(rec);
            return ResponseEntity.ok(rec);
        }

        @RequestMapping(method = GET, path = "getLast")
        public ResponseEntity<Record> getLast() {
            return ResponseEntity.of(recordService.getLast());
        }

        @RequestMapping(method = GET, path = "getByTime")
        public Collection<Record> getByTime(
                @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
            return recordService.getRange(start, end, MAX_PAGE_SIZE);
        }

    }


}