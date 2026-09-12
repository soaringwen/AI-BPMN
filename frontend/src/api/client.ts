import axios from 'axios';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120_000,
  headers: {
    'Content-Type': 'application/json'
  }
});

export interface ApiErrorPayload {
  code: string;
  message: string;
  requestId?: string;
  details?: { path: string; message: string }[];
}

export function toErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiErrorPayload | undefined;
    if (payload?.message) {
      return payload.message;
    }
    return error.message;
  }
  return error instanceof Error ? error.message : String(error);
}
