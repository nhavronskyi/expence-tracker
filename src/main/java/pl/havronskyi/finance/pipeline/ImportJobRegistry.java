package pl.havronskyi.finance.pipeline;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportJobRegistry {

    private static final Duration RETENTION = Duration.ofHours(1);

    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();

    public ImportJob create() {
        evictOld();
        ImportJob job = new ImportJob();
        jobs.put(job.getId(), job);
        return job;
    }

    public Optional<ImportJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void evictOld() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.values().removeIf(j -> j.isTerminal() && j.getCreatedAt().isBefore(cutoff));
    }
}
