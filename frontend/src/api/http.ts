import axios, { type AxiosRequestConfig } from 'axios';

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface ApiErrorPayload {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

/** Structured API error carrying the backend status/code/fieldErrors. */
export class ApiError extends Error {
  readonly status: number;

  readonly code?: string;

  readonly fieldErrors?: Record<string, string>;

  constructor(message: string, status: number, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const payload = error.response?.data as ApiErrorPayload | undefined;
      const status = error.response?.status ?? 0;
      const message = payload?.message
        || (status === 0 ? '网络请求失败，请检查网络或后端服务' : `请求失败（${status}）`);
      return Promise.reject(new ApiError(message, status, payload?.code, payload?.fieldErrors));
    }
    return Promise.reject(error instanceof Error ? error : new Error('网络请求失败'));
  },
);

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config);
  const body = response.data;
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 0) throw new Error(body.message || '请求失败');
    return body.data;
  }
  return body as unknown as T;
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request<T>({ ...config, method: 'GET', url });
}
