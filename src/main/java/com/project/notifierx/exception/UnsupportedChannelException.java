package com.project.notifierx.exception;

import com.project.notifierx.domain.ChannelType;

public class UnsupportedChannelException extends RuntimeException {

    private final ChannelType channel;

    public UnsupportedChannelException(ChannelType channel) {
        super("No notification strategy registered for channel: " + channel);
        this.channel = channel;
    }

    public UnsupportedChannelException(String message) {
        super(message);
        this.channel = null;
    }

    public ChannelType getChannel() {
        return channel;
    }
}