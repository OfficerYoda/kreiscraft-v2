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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Durable submissions and in-game decisions; public approval reflects the actual whitelist. */
public final class WhitelistRequests {
    public enum Status {
        PENDING, APPROVED, DECLINED
    }

    public record Entry(String playerName, String requestedAt, String submissionId, Status status, String resolvedAt,
            long revision) {
    }

    public record Result(int statusCode, Map<String, String> body) {
    }

    private static final Pattern NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    private final Path directory;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Consumer<Entry> onSubmission;
    private long windowStart;
    private int submissions;

    public WhitelistRequests(Path directory, ObjectMapper mapper, Clock clock) {
        this(directory, mapper, clock, entry -> {
        });
    }

    public WhitelistRequests(Path directory, ObjectMapper mapper, Clock clock, Consumer<Entry> onSubmission) {
        this.directory = directory;
        this.mapper = mapper;
        this.clock = clock;
        this.onSubmission = onSubmission;
    }

    // ponytail: one lock serializes local submission writes; per-name locks if contention matters.
    public synchronized Result get(String name, boolean submit, boolean whitelisted) throws IOException {
        return get(name, submit, whitelisted, false);
    }

    public synchronized Result get(String name, boolean submit, boolean whitelisted, boolean reopen)
            throws IOException {
        if (name == null || !NAME.matcher(name).matches()) {
            return new Result(400, Map.of("error", "invalid_name"));
        }
        Entry entry = read(name);
        boolean created = submit && !whitelisted && (entry == null || reopen && entry.status() == Status.DECLINED);
        if (created) {
            long now = clock.millis();
            if (now - windowStart >= 60_000) {
                windowStart = now;
                submissions = 0;
            }
            // ponytail: global 60-new-or-reopened-requests/minute quota bounds public disk writes;
            // add per-client limits at the reverse proxy if shared traffic needs isolation.
            if (submissions >= 60) {
                return new Result(429, Map.of("error", "rate_limited"));
            }
            entry = new Entry(entry == null ? name : entry.playerName(), clock.instant().toString(),
                    UUID.randomUUID().toString(), Status.PENDING, null, 0);
            write(entry);
            submissions++;
            onSubmission.accept(entry);
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("playerName", entry == null ? name : entry.playerName());
        body.put("status", whitelisted ? "whitelisted"
                : entry == null ? "not_requested"
                        : entry.status() == Status.DECLINED ? "declined" : "pending");
        if (entry != null) {
            body.put("requestedAt", entry.requestedAt());
            body.put("submissionId", entry.submissionId());
            if (entry.resolvedAt() != null && (whitelisted || entry.status() == Status.DECLINED)) {
                body.put("resolvedAt", entry.resolvedAt());
            }
        }
        return new Result(created ? 202 : 200, Map.copyOf(body));
    }

    public synchronized List<Entry> list() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            var entries = new java.util.ArrayList<Entry>();
            for (Path file : files.filter(p -> !p.getFileName().toString().startsWith(".")
                    && p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String filename = file.getFileName().toString();
                entries.add(read(filename.substring(0, filename.length() - 5)));
            }
            return List.copyOf(entries);
        }
    }

    /** Compare the snapshot before writing so stale menus cannot overwrite another decision or reopening. */
    public synchronized void resolve(Entry expected, Status status) throws IOException {
        if (status == null || status == Status.PENDING) {
            throw new IllegalArgumentException("A review must approve or decline");
        }
        if (!expected.equals(read(expected.playerName()))) {
            throw new IOException("Whitelist request changed; refresh the menu");
        }
        write(new Entry(expected.playerName(), expected.requestedAt(), expected.submissionId(), status,
                clock.instant().toString(), Math.incrementExact(expected.revision())));
    }

    private Entry read(String name) throws IOException {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IOException("Invalid whitelist name");
        }
        Path file = directory.resolve(name.toLowerCase(Locale.ROOT) + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        Entry entry = mapper.readValue(file.toFile(), Entry.class);
        if (entry == null || entry.playerName() == null || !entry.playerName().equalsIgnoreCase(name)
                || entry.requestedAt() == null || entry.submissionId() == null
                || entry.status() != null && entry.status() != Status.PENDING && entry.resolvedAt() == null) {
            throw new IOException("Invalid whitelist submission: " + file);
        }
        return entry.status() == null ? new Entry(entry.playerName(), entry.requestedAt(), entry.submissionId(),
                Status.PENDING, null, entry.revision()) : entry;
    }

    private void write(Entry entry) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".request-", ".json");
        try {
            mapper.writeValue(temporary.toFile(), entry);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, directory.resolve(entry.playerName().toLowerCase(Locale.ROOT) + ".json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
