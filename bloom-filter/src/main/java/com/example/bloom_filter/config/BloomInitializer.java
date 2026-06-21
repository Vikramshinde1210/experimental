package com.example.bloom_filter.config;

import com.example.bloom_filter.service.BloomFilterService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BloomInitializer {

    private final BloomFilterService bloomFilterService;

    @PostConstruct
    public void init() {
        bloomFilterService.initializeFromDatabase();
    }
}
