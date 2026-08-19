package com.project.notifierx.exception;

public class UserNotFoundException extends RuntimeException {

    private final String apiKey;

    public UserNotFoundException(String apiKey) {
        super("No user found for API key: " + apiKey);
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
}