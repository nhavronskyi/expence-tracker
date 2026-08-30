-- V1 declared these as CHAR(n), but the JPA entities map them as plain String
-- (VARCHAR). Hibernate's schema validator compares JDBC type codes, not just
-- length, so CHAR vs VARCHAR fails validation on every startup. No data loss:
-- CHAR(n) values are valid VARCHAR(n) values as-is.
ALTER TABLE account ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE txn ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE txn ALTER COLUMN dedup_key TYPE VARCHAR(64);
ALTER TABLE import_batch ALTER COLUMN sha256 TYPE VARCHAR(64);
