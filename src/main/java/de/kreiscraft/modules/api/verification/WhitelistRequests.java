package de.kreiscraft.modules.api.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable submissions only; approval comes from the actual Minecraft whitelist. */
public final class WhitelistRequests {
    public record Entry(String playerName, String requestedAt, String submissionId) {
    }

    public record Result(int statusCode, Map<String, String> body) {
    }

    private static final Pattern NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    private final Path directory;
    private final ObjectMapper mapper;
    private final Clock clock;
    private long windowStart;
    private int submissions;

    public WhitelistRequests(Path directory, ObjectMapper mapper, Clock clock) {
        this.directory = directory;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ponytail: one lock serializes local submission writes; per-name locks if contention matters.
    public synchronized Result get(String name, boolean submit, boolean whitelisted) throws IOException {
        if (name == null || !NAME.matcher(name).matches()) {
            return new Result(400, Map.of("error", "invalid_name"));
        }
        Path file = directory.resolve(name.toLowerCase(Locale.ROOT) + ".json");
        Entry entry = null;
        if (Files.exists(file)) {
            entry = mapper.readValue(file.toFile(), Entry.class);
            if (entry == null || entry.playerName() == null || !entry.playerName().equalsIgnoreCase(name)
                    || entry.requestedAt() == null || entry.submissionId() == null) {
                throw new IOException("Invalid whitelist submission: " + file);
            }
        }
        boolean created = submit && entry == null && !whitelisted;
        if (created) {
            long now = clock.millis();
            if (now - windowStart >= 60_000) {
                windowStart = now;
                submissions = 0;
            }
            // ponytail: global 60-new-names/minute quota bounds public disk writes;
            // add per-client limits at the reverse proxy if shared traffic needs isolation.
            if (submissions >= 60) {
                return new Result(429, Map.of("error", "rate_limited"));
            }
            entry = new Entry(name, clock.instant().toString(), UUID.randomUUID().toString());
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".request-", ".json");
            try {
                mapper.writeValue(temporary.toFile(), entry);
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            submissions++;
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("playerName", entry == null ? name : entry.playerName());
        body.put("status", whitelisted ? "whitelisted" : entry == null ? "not_requested" : "pending");
        if (entry != null) {
            body.put("requestedAt", entry.requestedAt());
            body.put("submissionId", entry.submissionId());
        }
        // ponytail: decisions/resolvedAt/reopen belong to the future in-game review issue.
        return new Result(created ? 202 : 200, Map.copyOf(body));
    }
}
