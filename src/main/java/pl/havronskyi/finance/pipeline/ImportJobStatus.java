package pl.havronskyi.finance.pipeline;

public record ImportJobStatus(
        String jobId,
        ImportJob.Phase phase,
        int processed,
        int total,
        ImportSummary summary,
        String error
) {
    public static ImportJobStatus of(ImportJob job) {
        return new ImportJobStatus(job.getId(), job.getPhase(), job.getProcessed(), job.getTotal(),
                job.getSummary(), job.getError());
    }
}
