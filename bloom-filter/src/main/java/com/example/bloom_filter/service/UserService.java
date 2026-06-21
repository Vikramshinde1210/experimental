package com.example.bloom_filter.service;

import com.example.bloom_filter.entity.User;
import com.example.bloom_filter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository repository;
    private final BloomFilterService bloomFilterService;

    public Map<String, Object> findUser(String email) {
        boolean inBloom = bloomFilterService.mightContainWithRecovery(email);

        log.info("Lookup email={} bloomResult={}", email, inBloom);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("email", email);
        response.put("bloomSaysPresent", inBloom);

        if (!inBloom) {
            response.put("result", "DEFINITELY_NOT_PRESENT");
            response.put("dbChecked", false);
            response.put("message", "Email is definitely not registered (no DB query needed)");
            return response;
        }

        boolean inDb = repository.findByEmail(email).isPresent();
        response.put("dbChecked", true);

        if (inDb) {
            response.put("result", "FOUND_IN_DB");
            response.put("message", "Email exists in database");
        } else {
            response.put("result", "FALSE_POSITIVE");
            response.put("message", "Bloom filter said maybe, but email is NOT in database");
        }

        return response;
    }

    public User createUser(User user) {
        if (user.getId() == null) {
            user.setId(repository.count() + 1);
        }
        User savedUser = repository.save(user);
        boolean added = bloomFilterService.addToBloom(savedUser.getEmail());
        log.info("Created user email={} bloomAdded={}", savedUser.getEmail(), added);
        return savedUser;
    }

    public int loadSampleUsers(int count) {
        repository.deleteAll();
        bloomFilterService.initializeFromDatabase();

        for (int i = 1; i <= count; i++) {
            User user = new User();
            user.setId((long) i);
            user.setEmail("user" + i + "@gmail.com");
            createUser(user);
        }

        return count;
    }
}
