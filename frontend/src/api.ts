import type {
  Account,
  CategoryOption,
  ImportSummary,
  MonthlyReport,
  ResolveRequest,
  ReviewCard,
} from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

export function getAccounts(): Promise<Account[]> {
  return fetch("/api/accounts").then((res) => json(res));
}

export function getCategories(): Promise<CategoryOption[]> {
  return fetch("/api/categories").then((res) => json(res));
}

export function importFile(
  accountId: number,
  file: File,
): Promise<ImportSummary> {
  const body = new FormData();
  body.set("file", file);
  return fetch(`/api/import?accountId=${accountId}`, {
    method: "POST",
    body,
  }).then((res) => json(res));
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

export function getMonthlyStats(
  month: string,
  scope: string,
): Promise<MonthlyReport> {
  return fetch(`/api/stats/monthly?month=${month}&scope=${scope}`).then((res) =>
    json(res),
  );
}
