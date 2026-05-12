package com.example.virtual.threads;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Java21VirtualThreadsPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(Java21VirtualThreadsPocApplication.class, args);
    }

    @GetMapping("/io-task")
    public String ioTask() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2); // Simulate a blocking I/O operation
        return "I/O Task Completed on thread: " + Thread.currentThread().getName();
    }

}
