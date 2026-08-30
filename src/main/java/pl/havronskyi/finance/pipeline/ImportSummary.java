package pl.havronskyi.finance.pipeline;

public record ImportSummary(
        Long batchId,
        int rowsParsed,
        int inserted,
        int duplicatesSkipped,
        int internalTransfers,
        int categorizedByRule,
        int categorizedByLlm,
        int queuedForReview
) {
}
