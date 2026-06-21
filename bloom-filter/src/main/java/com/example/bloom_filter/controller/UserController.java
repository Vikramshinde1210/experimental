package com.example.bloom_filter.controller;

import com.example.bloom_filter.entity.User;
import com.example.bloom_filter.service.BloomFilterService;
import com.example.bloom_filter.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final BloomFilterService bloomFilterService;

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String email) {
        return userService.findUser(email);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.createUser(user);
    }

    @PostMapping("/load")
    public Map<String, Object> loadUsers(
            @RequestParam(defaultValue = "100") int count) {

        int loaded = userService.loadSampleUsers(count);
        return Map.of(
                "message", "Loaded sample users",
                "count", loaded,
                "stats", bloomFilterService.getStats()
        );
    }

    @GetMapping("/bloom/stats")
    public Map<String, String> bloomStats() {
        return bloomFilterService.getStats();
    }

    @PostMapping("/bloom/sync")
    public Map<String, Object> syncBloom() {
        bloomFilterService.initializeFromDatabase();
        return Map.of(
                "message", "Bloom filter re-synced from database",
                "stats", bloomFilterService.getStats()
        );
    }

    @GetMapping("/bloom/benchmark")
    public Map<String, Object> benchmark(
            @RequestParam(defaultValue = "10000") int iterations) {
        return bloomFilterService.runBenchmark(iterations);
    }

    @GetMapping("/bloom/false-positives")
    public Map<String, Object> falsePositives(
            @RequestParam(defaultValue = "10000") int samples) {
        return bloomFilterService.measureFalsePositives(samples);
    }
}
