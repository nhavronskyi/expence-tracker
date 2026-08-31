-- A workspace is a folder-like container: its own bank accounts, categories, merchant
-- rules and transactions. The LLM categorizer and rule engine only ever see data from
-- one workspace at a time. All pre-existing data moves into a single seeded workspace.
CREATE TABLE workspace
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO workspace (name) VALUES ('Personal');

ALTER TABLE account ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE account SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE account ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_account_workspace ON account (workspace_id);

ALTER TABLE category ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE category SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE category ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_category_workspace ON category (workspace_id);

ALTER TABLE merchant_rule ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE merchant_rule SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE merchant_rule ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_merchant_rule_workspace ON merchant_rule (workspace_id);

ALTER TABLE import_batch ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE import_batch SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE import_batch ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_import_batch_workspace ON import_batch (workspace_id);

ALTER TABLE txn ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE txn SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE txn ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_txn_workspace ON txn (workspace_id);

ALTER TABLE review_item ADD COLUMN workspace_id BIGINT REFERENCES workspace (id);
UPDATE review_item SET workspace_id = (SELECT id FROM workspace ORDER BY id LIMIT 1);
ALTER TABLE review_item ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX idx_review_item_workspace ON review_item (workspace_id);

-- Uniqueness that used to be global now only needs to hold within a workspace.
-- (import_batch.sha256 has no uniqueness constraint since V4 - re-importing the same
-- file is intentionally allowed - so it's left untouched here.)
ALTER TABLE account DROP CONSTRAINT account_iban_key;
ALTER TABLE account ADD CONSTRAINT uq_account_workspace_iban UNIQUE (workspace_id, iban);

ALTER TABLE category DROP CONSTRAINT category_code_key;
ALTER TABLE category ADD CONSTRAINT uq_category_workspace_code UNIQUE (workspace_id, code);

ALTER TABLE merchant_rule DROP CONSTRAINT merchant_rule_match_type_pattern_key;
ALTER TABLE merchant_rule
    ADD CONSTRAINT uq_merchant_rule_workspace UNIQUE (workspace_id, match_type, pattern);
