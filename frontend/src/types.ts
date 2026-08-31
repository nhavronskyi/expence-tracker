// Categories are user-extensible (added from the app), so this is just a string,
// not a fixed union - the valid set lives in the `category` table, fetched via
// getCategories().
export type Category = string;

export type TxnKind = "EXPENSE" | "INCOME" | "INTERNAL_TRANSFER" | "UNKNOWN";

export type AccountScope = "PERSONAL" | "BUSINESS";

export type AccountType = "CURRENT" | "CREDIT_CARD";

export interface CategoryOption {
  name: Category;
  label: string;
}

export interface Account {
  id: number;
  label: string;
  iban: string;
  scope: AccountScope;
  type: AccountType;
  currency: string;
}

export interface NewAccountRequest {
  iban: string;
  label: string;
  scope: AccountScope;
  type: AccountType;
  currency: string;
}

export interface NewCategoryRequest {
  label: string;
  definition: string;
}

export interface Suggestion {
  category: Category;
  confidence: number;
  reason: string;
}

export interface ReviewCard {
  reviewId: number;
  txnId: number;
  merchant: string;
  description: string;
  amount: number;
  currency: string;
  txnDate: string;
  kind: TxnKind;
  suggestionsJson: string;
}

export interface ResolveRequest {
  category: Category;
  kind: TxnKind;
  learnRule: boolean;
}

export interface ImportSummary {
  batchId: number;
  rowsParsed: number;
  inserted: number;
  duplicatesSkipped: number;
  internalTransfers: number;
  categorizedByRule: number;
  categorizedByLlm: number;
  queuedForReview: number;
}

export type ImportPhase =
  "PARSING" | "CATEGORIZING" | "DONE" | "CANCELLED" | "FAILED";

export interface ImportJobStatus {
  jobId: string;
  phase: ImportPhase;
  processed: number;
  total: number;
  summary: ImportSummary | null;
  error: string | null;
}

export interface PeriodReport {
  from: string;
  to: string;
  scope: string;
  totalExpenses: number;
  totalIncome: number;
  net: number;
  byCategory: Partial<Record<Category, number>>;
  excludedInternalTransfers: number;
  uncategorizedCount: number;
  warnings: string[];
  nettedCounterparties: string[];
}

export interface CategoryTransaction {
  txnId: number;
  txnDate: string;
  merchant: string;
  description: string;
  amount: number;
  currency: string;
  kind: TxnKind;
}
