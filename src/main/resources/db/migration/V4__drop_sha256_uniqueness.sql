-- Re-importing the same export (e.g. a periodic full-history export, or to recover rows you
-- deleted) must be possible. The per-row dedup_key check on txn already handles correctness
-- at the row level - existing rows are skipped, missing ones re-inserted - so this whole-file
-- gate was redundant and actively blocked that workflow.
ALTER TABLE import_batch DROP CONSTRAINT import_batch_sha256_key;
