package com.example.bloom_filter.service;

import com.example.bloom_filter.repository.UserRepository;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.ArrayOutput;
import io.lettuce.core.output.BooleanOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class BloomFilterService {

    static final String BLOOM_KEY = "usersBloom";

    private final RedisCommands<String, String> redis;
    private final UserRepository repository;

    @Value("${bloom.error-rate:0.01}")
    private double errorRate;

    @Value("${bloom.capacity:10000}")
    private long capacity;

    public void initializeFromDatabase() {
        redis.del(BLOOM_KEY);

        redis.dispatch(
                new RedisBloomCommand("BF.RESERVE"),
                new StatusOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(BLOOM_KEY)
                        .add(String.valueOf(errorRate))
                        .add(String.valueOf(capacity))
        );

        for (var user : repository.findAll()) {
            addToBloom(user.getEmail());
        }

        long synced = repository.count();

        log.info("Bloom filter initialized: synced {} emails from database", synced);
    }

    public boolean isBloomAvailable() {
        return redis.exists(BLOOM_KEY) > 0;
    }

    public void ensureBloomAvailable() {
        if (!isBloomAvailable()) {
            log.warn("Bloom filter key missing in Redis — re-syncing from database");
            initializeFromDatabase();
        }
    }

    public boolean mightContain(String email) {
        Boolean result = redis.dispatch(
                new RedisBloomCommand("BF.EXISTS"),
                new BooleanOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(BLOOM_KEY)
                        .add(email)
        );

        return Boolean.TRUE.equals(result);
    }

    public boolean mightContainWithRecovery(String email) {
        if (!isBloomAvailable()) {
            log.warn("Bloom filter key missing in Redis — re-syncing from database");
            initializeFromDatabase();
        }
        return mightContain(email);
    }

    public boolean addToBloom(String email) {
        ensureBloomAvailable();

        Boolean result = redis.dispatch(
                new RedisBloomCommand("BF.ADD"),
                new BooleanOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(BLOOM_KEY)
                        .add(email)
        );

        return Boolean.TRUE.equals(result);
    }

    public Map<String, String> getStats() {
        ensureBloomAvailable();

        List<Object> raw = redis.dispatch(
                new RedisBloomCommand("BF.INFO"),
                new ArrayOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8).add(BLOOM_KEY)
        );

        Map<String, String> stats = new LinkedHashMap<>();
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            stats.put(String.valueOf(raw.get(i)), String.valueOf(raw.get(i + 1)));
        }

        stats.put("databaseUserCount", String.valueOf(repository.count()));
        stats.put("bloomKeyPresent", String.valueOf(isBloomAvailable()));
        return stats;
    }

    public Map<String, Object> runBenchmark(int iterations) {
        ensureBloomAvailable();

        List<String> dbEmails = repository.findAll().stream()
                .map(user -> user.getEmail())
                .limit(Math.max(1, iterations))
                .toList();

        if (dbEmails.isEmpty()) {
            return Map.of("error", "No users in database. POST /users/load first.");
        }

        long bloomNs = 0;
        for (int i = 0; i < iterations; i++) {
            String email = dbEmails.get(i % dbEmails.size());
            long start = System.nanoTime();
            mightContain(email);
            bloomNs += System.nanoTime() - start;
        }

        long dbNs = 0;
        for (int i = 0; i < iterations; i++) {
            String email = dbEmails.get(i % dbEmails.size());
            long start = System.nanoTime();
            repository.findByEmail(email);
            dbNs += System.nanoTime() - start;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iterations", iterations);
        result.put("bloomTotalMs", bloomNs / 1_000_000.0);
        result.put("bloomAvgMicros", bloomNs / (double) iterations / 1_000);
        result.put("dbTotalMs", dbNs / 1_000_000.0);
        result.put("dbAvgMicros", dbNs / (double) iterations / 1_000);
        result.put("speedupFactor", dbNs / (double) bloomNs);
        return result;
    }

    public Map<String, Object> measureFalsePositives(int samples) {
        ensureBloomAvailable();

        int falsePositives = 0;
        List<String> examples = new ArrayList<>();

        for (int i = 0; i < samples; i++) {
            String fakeEmail = "fp-" + ThreadLocalRandom.current().nextLong() + "@not-in-db.com";

            if (mightContain(fakeEmail)) {
                falsePositives++;
                if (examples.size() < 5) {
                    examples.add(fakeEmail);
                }
            }
        }

        double observedRate = falsePositives / (double) samples;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samplesTested", samples);
        result.put("falsePositives", falsePositives);
        result.put("observedFalsePositiveRate", observedRate);
        result.put("configuredErrorRate", errorRate);
        result.put("exampleFalsePositives", examples);
        result.put("note", "False positives are expected. Bloom filter never produces false negatives for inserted items.");
        return result;
    }
}
