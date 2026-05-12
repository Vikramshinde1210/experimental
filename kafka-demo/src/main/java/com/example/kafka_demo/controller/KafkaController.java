package com.example.kafka_demo.controller;

import org.springframework.web.bind.annotation.*;

import com.example.kafka_demo.service.KafkaProducer;

@RestController
@RequestMapping("/kafka")
public class KafkaController {
    private final KafkaProducer producer;

    public KafkaController(KafkaProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/send/{message}")
    public String send(@PathVariable String message) {
        producer.sendMessage("test-topic", message);
        return "✅ Message sent: " + message;
    }
}
