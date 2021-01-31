package com.project.redis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.project.redis.RedisApplication.Record;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisApplicationTests {

    @TestConfiguration
    public static class EmbededRedisTestConfiguration {

        private final redis.embedded.RedisServer redisServer;

        public EmbededRedisTestConfiguration() throws IOException {
            this.redisServer = new redis.embedded.RedisServer(6379);
        }

        @PostConstruct
        public void startRedis() {
            this.redisServer.start();
        }

        @PreDestroy
        public void stopRedis() {
            this.redisServer.stop();
        }


    }

    // This is used a source of truth for tests
    static class RangeQueryList {
        List<Record> list;

        public RangeQueryList(List<Record> list) {
            assertTrue(isSorted(list, Comparator.comparing(x -> x.created)), "The collection is not sorted");
            this.list = list;
        }

        public List<Record> getInRange(LocalDateTime from, LocalDateTime to) {
            return list.stream().filter(x -> x.created.isAfter(from) && x.created.isBefore(to) ||
                    (x.created.equals(from) || x.created.equals(to)))
                    .collect(Collectors.toList());
        }
    }

    static LocalDateTime startupTime = LocalDateTime.now();

    static LocalDateTime time(TemporalAmount delta) {
        return startupTime.plus(delta);
    }


    static Record record(String message, TemporalAmount delta) {
        return new Record(UUID.randomUUID(), time(delta), message);
    }


    static Stream<Record> recordRange(TemporalAmount interval, int count, int tagStartFrom) {
        return IntStream.range(0, count)
                .mapToObj(i -> Record.builder()
                        .id(UUID.randomUUID())
                        .created(startupTime.plusSeconds(interval.get(ChronoUnit.SECONDS) * i))
                        .content("test_message_" + (tagStartFrom + i))
                        .build());
    }

    static <T> boolean isSorted(Collection<T> collection, Comparator<T> comp) {
        if (collection.isEmpty()) return true;
        Iterator<T> it = collection.iterator();
        T last = it.next();
        while (it.hasNext()) {
            T curr = it.next();
            if (comp.compare(curr, last) < 0) {
                return false;
            }
            last = curr;
        }
        return true;
    }


    @Autowired
    RedisApplication.RecordService recordService;


    @Test
    void basicTest() {


        // Verify empty when started
        assertTrue(recordService.getLast().isEmpty());

        // Simple getLast
        Duration simpleTestsTime = ofSeconds(-1); // We need this in order not to interfere with the range query tests
        Record r1Request = record("msg0", simpleTestsTime);
        recordService.save(r1Request);
        Record r1Result = recordService.getLast().get();
        assertEquals(r1Result, r1Result);

        // Assert two requests with same record throw an exception
        try {
            recordService.save(r1Request);
            Assertions.fail("Should throw an exception");
        } catch (RuntimeException e) {
        }

        // Assert two different records with same timestamps are stored
        r1Request.content = "msg1";
        recordService.save(r1Request);
        Collection<Record> sameTimestampRecords = recordService.getRange(time(simpleTestsTime), time(simpleTestsTime));
        assertEquals(2, sameTimestampRecords.size());


        // Store a 1000 records with 1 minute of interval spacing and perform random queries
        List<Record> list = recordRange(ofMinutes(1), 3, 0).collect(Collectors.toCollection(ArrayList::new));
        RangeQueryList rql = new RangeQueryList(list);
        list.forEach(recordService::save);

        Random rand = new Random();

        for (int i = 0; i < 100; ++i) {
            int start = rand.nextInt(list.size());
            int end = start + rand.nextInt(list.size());
            List<Record> result = new ArrayList<>(recordService.getRange(time(ofMinutes(start)), time(ofMinutes(end))));
            assertEquals(rql.getInRange(time(ofMinutes(start)), time(ofMinutes(end))), result,
                    String.format("Failed test for start=%s, end=%s, seed={}", start, end));

        }


    }
}