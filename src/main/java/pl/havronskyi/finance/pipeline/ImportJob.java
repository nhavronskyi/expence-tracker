package pl.havronskyi.finance.pipeline;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable, thread-safe progress/status for one background import run - written from the
 * virtual thread doing the work, read from HTTP request threads polling status.
 */
public class ImportJob {

    public enum Phase {PARSING, CATEGORIZING, DONE, CANCELLED, FAILED}

    private final String id = UUID.randomUUID().toString();
    private final Instant createdAt = Instant.now();
    private volatile Phase phase = Phase.PARSING;
    private final AtomicInteger processed = new AtomicInteger(0);
    private volatile int total = 0;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile ImportSummary summary;
    private volatile String error;

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getProcessed() {
        return processed.get();
    }

    public void setProcessed(int value) {
        processed.set(value);
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public void requestCancel() {
        cancelRequested.set(true);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public ImportSummary getSummary() {
        return summary;
    }

    public void complete(ImportSummary summary) {
        this.summary = summary;
        this.phase = cancelRequested.get() ? Phase.CANCELLED : Phase.DONE;
    }

    public String getError() {
        return error;
    }

    public void fail(String error) {
        this.error = error;
        this.phase = Phase.FAILED;
    }

    public boolean isTerminal() {
        return phase == Phase.DONE || phase == Phase.CANCELLED || phase == Phase.FAILED;
    }
}
