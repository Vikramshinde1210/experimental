package com.example.bloom_filter.service;

import io.lettuce.core.protocol.ProtocolKeyword;

public class RedisBloomCommand implements ProtocolKeyword {

    private final String command;

    public RedisBloomCommand(String command) {
        this.command = command;
    }

    @Override
    public byte[] getBytes() {
        return command.getBytes();
    }
}