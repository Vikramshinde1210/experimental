package com.example.kafka_demo.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
    @KafkaListener(topics = "test-topic", groupId = "demo-group")
    public void consume(String message) {
        System.out.println("📥 Received: " + message);
    }
}
