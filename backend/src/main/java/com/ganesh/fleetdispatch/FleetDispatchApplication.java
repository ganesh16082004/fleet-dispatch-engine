package com.ganesh.fleetdispatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FleetDispatchApplication {
    public static void main(String[] args) {
        loadLocalDotEnv();
        SpringApplication.run(FleetDispatchApplication.class, args);
    }

    /**
     * Loads local development settings from .env without adding another runtime
     * dependency. Real environment variables always win over values in .env.
     */
    private static void loadLocalDotEnv() {
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();

                if (key.isEmpty() || System.getenv(key) != null || System.getProperty(key) != null) {
                    continue;
                }

                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                System.setProperty(key, value);
            }
        } catch (IOException ignored) {
            // Keep startup behavior unchanged when no readable .env is available.
        }
    }
}
