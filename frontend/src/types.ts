export type Category =
  | "APARTMENTS"
  | "PAYMENTS"
  | "TRANSPORT"
  | "CLOTHES"
  | "ELECTRONICS"
  | "MEBLES"
  | "FRIDGE"
  | "DELIVERY"
  | "HOBBY"
  | "PRESENTS"
  | "RESTAURANTS"
  | "TRAVELING"
  | "TOOLS"
  | "SPORT"
  | "HEALTH"
  | "INVESTMENTS"
  | "TAX";

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

export interface Suggestion {
  category: Category;
  confidence: number;
  reason: string;
}

export interface ReviewCard {
  reviewId: number;
  txnId: number;
  question: string;
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

export interface MonthlyReport {
  month: string;
  scope: string;
  totalExpenses: number;
  totalIncome: number;
  net: number;
  byCategory: Partial<Record<Category, number>>;
  excludedInternalTransfers: number;
  uncategorizedCount: number;
  warnings: string[];
}
