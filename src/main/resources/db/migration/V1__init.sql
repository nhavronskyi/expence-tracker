-- The user's accounts. Every own IBAN here => a transfer to it is internal.
CREATE TABLE account
(
    id       BIGSERIAL PRIMARY KEY,
    iban     VARCHAR(34) UNIQUE,
    label    VARCHAR(120) NOT NULL,
    scope    VARCHAR(16)  NOT NULL, -- PERSONAL | BUSINESS
    type     VARCHAR(16)  NOT NULL, -- CURRENT | CREDIT_CARD
    currency CHAR(3)      NOT NULL DEFAULT 'PLN',
    active   BOOLEAN      NOT NULL DEFAULT TRUE
);

-- One uploaded file = one batch. sha256 protects against importing it twice.
CREATE TABLE import_batch
(
    id          BIGSERIAL PRIMARY KEY,
    filename    VARCHAR(255) NOT NULL,
    sha256      CHAR(64)     NOT NULL UNIQUE,
    account_id  BIGINT       NOT NULL REFERENCES account (id),
    format      VARCHAR(32)  NOT NULL,
    imported_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    row_count   INT          NOT NULL DEFAULT 0
);

-- The raw row. Never mutated - the source of truth when the parser changes.
CREATE TABLE raw_transaction
(
    id       BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    line_no  INT    NOT NULL,
    raw_line TEXT   NOT NULL
);

CREATE TABLE txn
(
    id                BIGSERIAL PRIMARY KEY,
    raw_id            BIGINT      NOT NULL REFERENCES raw_transaction (id),
    account_id        BIGINT      NOT NULL REFERENCES account (id),
    booked_date       DATE        NOT NULL,
    txn_date          DATE        NOT NULL,
    amount_minor      BIGINT      NOT NULL, -- negative = expense, positive = income
    currency          CHAR(3)     NOT NULL,
    amount_pln_minor  BIGINT,               -- converted at the NBP rate for txn_date
    counterparty_iban VARCHAR(34),
    merchant_raw      VARCHAR(512),
    merchant_norm     VARCHAR(255),
    description       TEXT,
    kind              VARCHAR(24) NOT NULL, -- EXPENSE | INCOME | INTERNAL_TRANSFER | UNKNOWN
    category          VARCHAR(32),
    category_source   VARCHAR(16),          -- IBAN | RULE | LLM | MANUAL
    confidence        NUMERIC(3, 2),
    needs_review      BOOLEAN     NOT NULL DEFAULT FALSE,
    transfer_group    UUID,                 -- both legs of an internal transfer share this
    dedup_key         CHAR(64)    NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_txn_month ON txn (txn_date);
CREATE INDEX idx_txn_review ON txn (needs_review) WHERE needs_review;
CREATE INDEX idx_txn_merchant ON txn (merchant_norm);

-- Learned rules. Every answer in the review queue creates an entry here.
CREATE TABLE merchant_rule
(
    id         BIGSERIAL PRIMARY KEY,
    match_type VARCHAR(16)  NOT NULL, -- EXACT | PREFIX | REGEX
    pattern    VARCHAR(255) NOT NULL,
    category   VARCHAR(32)  NOT NULL,
    kind       VARCHAR(24)  NOT NULL,
    priority   INT          NOT NULL DEFAULT 100,
    hit_count  INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (match_type, pattern)
);

CREATE TABLE review_item
(
    id          BIGSERIAL PRIMARY KEY,
    txn_id      BIGINT      NOT NULL REFERENCES txn (id) ON DELETE CASCADE,
    question    TEXT        NOT NULL,
    suggestions TEXT        NOT NULL, -- JSON: [{category, confidence, reason}]
    status      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolved_at TIMESTAMPTZ,
    UNIQUE (txn_id)
);
