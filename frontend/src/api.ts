import type {
  Account,
  CategoryOption,
  CategoryTransaction,
  ImportJobStatus,
  NewAccountRequest,
  NewAccountResponse,
  NewCategoryRequest,
  NewWorkspaceRequest,
  PeriodReport,
  ResolveRequest,
  ReviewCard,
  UpdateAccountRequest,
  UpdateCategoryRequest,
  Workspace,
} from "./types";

let currentWorkspaceId: number | null = null;

export function setWorkspaceId(id: number | null): void {
  currentWorkspaceId = id;
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

// Injects the current workspace into every request. getWorkspaces/createWorkspace/
// deleteWorkspace bypass this - they're how a workspace is discovered/picked in the
// first place.
function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (currentWorkspaceId !== null) {
    headers.set("X-Workspace-Id", String(currentWorkspaceId));
  }
  return fetch(path, { ...init, headers });
}

export function getWorkspaces(): Promise<Workspace[]> {
  return fetch("/api/workspaces").then((res) => json(res));
}

export function createWorkspace(
  request: NewWorkspaceRequest,
): Promise<Workspace> {
  return fetch("/api/workspaces", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export async function deleteWorkspace(id: number): Promise<void> {
  const res = await fetch(`/api/workspaces/${id}`, { method: "DELETE" });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export function getAccounts(includeInactive = false): Promise<Account[]> {
  return apiFetch(
    `/api/accounts${includeInactive ? "?includeInactive=true" : ""}`,
  ).then((res) => json(res));
}

export function createAccount(
  request: NewAccountRequest,
): Promise<NewAccountResponse> {
  return apiFetch("/api/accounts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function updateAccount(
  id: number,
  request: UpdateAccountRequest,
): Promise<NewAccountResponse> {
  return apiFetch(`/api/accounts/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function getCategories(
  includeInactive = false,
): Promise<CategoryOption[]> {
  return apiFetch(
    `/api/categories${includeInactive ? "?includeInactive=true" : ""}`,
  ).then((res) => json(res));
}

export function createCategory(
  request: NewCategoryRequest,
): Promise<CategoryOption> {
  return apiFetch("/api/categories", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => json(res));
}

export function updateCategory(
  id: number,
  request: UpdateCategoryRequest,
): Promise<CategoryOption> {
  return apiFetch(`/api/categories/${id}`, {
    method: "PATCH",
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
  return apiFetch(`/api/import?accountId=${accountId}`, {
    method: "POST",
    body,
  }).then((res) => json(res));
}

export function getImportStatus(jobId: string): Promise<ImportJobStatus> {
  return apiFetch(`/api/import/${jobId}/status`).then((res) => json(res));
}

export async function cancelImport(jobId: string): Promise<void> {
  const res = await apiFetch(`/api/import/${jobId}/cancel`, {
    method: "POST",
  });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export async function clearTransactions(): Promise<void> {
  const res = await apiFetch("/api/import/clear", { method: "POST" });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export function getTransactionCount(): Promise<{ total: number }> {
  return apiFetch("/api/stats/count").then((res) => json(res));
}

export function backfillFx(): Promise<{ fixed: number }> {
  return apiFetch("/api/import/backfill-fx", { method: "POST" }).then((res) =>
    json(res),
  );
}

export function renormalizeMerchants(): Promise<{ changed: number }> {
  return apiFetch("/api/import/renormalize-merchants", {
    method: "POST",
  }).then((res) => json(res));
}

export function reclassifyTransfers(): Promise<{ reclassified: number }> {
  return apiFetch("/api/import/reclassify-transfers", {
    method: "POST",
  }).then((res) => json(res));
}

export function getOpenReviews(): Promise<ReviewCard[]> {
  return apiFetch("/api/review").then((res) => json(res));
}

export async function resolveReview(
  reviewId: number,
  request: ResolveRequest,
): Promise<void> {
  const res = await apiFetch(`/api/review/${reviewId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}

export function getStats(from: string, to: string): Promise<PeriodReport> {
  return apiFetch(`/api/stats/range?from=${from}&to=${to}`).then((res) =>
    json(res),
  );
}

export function getCategoryTransactions(
  category: string,
  from: string,
  to: string,
): Promise<CategoryTransaction[]> {
  return apiFetch(
    `/api/stats/transactions?category=${encodeURIComponent(category)}&from=${from}&to=${to}`,
  ).then((res) => json(res));
}

export function getTransfers(
  from: string,
  to: string,
): Promise<CategoryTransaction[]> {
  return apiFetch(`/api/stats/transfers?from=${from}&to=${to}`).then((res) =>
    json(res),
  );
}

export async function recategorizeTransaction(
  txnId: number,
  request: ResolveRequest,
): Promise<void> {
  const res = await apiFetch(`/api/transactions/${txnId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
}
