import type {
  Account,
  CategoryOption,
  CategoryTransaction,
  ImportJobStatus,
  NewAccountRequest,
  NewAccountResponse,
  NewCategoryRequest,
  PeriodReport,
  ResolveRequest,
  ReviewCard,
  UpdateAccountRequest,
} from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

export function getAccounts(includeInactive = false): Promise<Account[]> {
  return fetch(
    `/api/accounts${includeInactive ? "?includeInactive=true" : ""}`,
  ).then((res) => json(res));
}

export function createAccount(
  request: NewAccountRequest,
): Promise<NewAccountResponse> {
  return fetch("/api/accounts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function updateAccount(
  id: number,
  request: UpdateAccountRequest,
): Promise<NewAccountResponse> {
  return fetch(`/api/accounts/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function getCategories(): Promise<CategoryOption[]> {
  return fetch("/api/categories").then((res) => json(res));
}

export function createCategory(
  request: NewCategoryRequest,
): Promise<CategoryOption> {
  return fetch("/api/categories", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function startImport(
  accountId: number,
  file: File,
): Promise<{ jobId: string }> {
  const body = new FormData();
  body.set("file", file);
  return fetch(`/api/import?accountId=${accountId}`, {
    method: "POST",
    body,
  }).then((res) => json(res));
}

export function getImportStatus(jobId: string): Promise<ImportJobStatus> {
  return fetch(`/api/import/${jobId}/status`).then((res) => json(res));
}

export async function cancelImport(jobId: string): Promise<void> {
  const res = await fetch(`/api/import/${jobId}/cancel`, { method: "POST" });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export async function clearTransactions(): Promise<void> {
  const res = await fetch("/api/import/clear", { method: "POST" });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export function getTransactionCount(): Promise<{ total: number }> {
  return fetch("/api/stats/count").then((res) => json(res));
}

export function backfillFx(): Promise<{ fixed: number }> {
  return fetch("/api/import/backfill-fx", { method: "POST" }).then((res) =>
    json(res),
  );
}

export function renormalizeMerchants(): Promise<{ changed: number }> {
  return fetch("/api/import/renormalize-merchants", { method: "POST" }).then(
    (res) => json(res),
  );
}

export function reclassifyTransfers(): Promise<{ reclassified: number }> {
  return fetch("/api/import/reclassify-transfers", { method: "POST" }).then(
    (res) => json(res),
  );
}

export function getOpenReviews(): Promise<ReviewCard[]> {
  return fetch("/api/review").then((res) => json(res));
}

export async function resolveReview(
  reviewId: number,
  request: ResolveRequest,
): Promise<void> {
  const res = await fetch(`/api/review/${reviewId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export function getStats(from: string, to: string): Promise<PeriodReport> {
  return fetch(`/api/stats/range?from=${from}&to=${to}`).then((res) =>
    json(res),
  );
}

export function getCategoryTransactions(
  category: string,
  from: string,
  to: string,
): Promise<CategoryTransaction[]> {
  return fetch(
    `/api/stats/transactions?category=${encodeURIComponent(category)}&from=${from}&to=${to}`,
  ).then((res) => json(res));
}

export function getTransfers(
  from: string,
  to: string,
): Promise<CategoryTransaction[]> {
  return fetch(`/api/stats/transfers?from=${from}&to=${to}`).then((res) =>
    json(res),
  );
}

export async function recategorizeTransaction(
  txnId: number,
  request: ResolveRequest,
): Promise<void> {
  const res = await fetch(`/api/transactions/${txnId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}
