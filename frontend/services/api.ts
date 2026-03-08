const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;

export async function apiFetch(
  path: string,
  options: RequestInit = {}
) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || "API error");
  }

  return res.json();
}