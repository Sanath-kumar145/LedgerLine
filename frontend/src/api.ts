import type {
  AuthResponse,
  Account,
  AccountStatement,
  TransactionResponse,
  TransferResponse,
  AccountReconciliation,
  SystemReconciliation,
  ApiError,
  UserProfile,
} from './types';

// API base URL configured via environment variable with fallback
export const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const TOKEN_KEY = 'ledgerline_jwt_token';
const USER_KEY = 'ledgerline_user_profile';

export const getToken = (): string | null => localStorage.getItem(TOKEN_KEY);
export const setToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token);
export const removeToken = (): void => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

export const getStoredUser = (): UserProfile | null => {
  const data = localStorage.getItem(USER_KEY);
  return data ? JSON.parse(data) : null;
};
export const setStoredUser = (user: UserProfile): void => {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
};

export class ApiException extends Error {
  status: number;
  data: ApiError;

  constructor(status: number, data: ApiError) {
    super(data.detail || data.title || `API error ${status}`);
    this.name = 'ApiException';
    this.status = status;
    this.data = data;
  }
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers || {});
  headers.set('Content-Type', 'application/json');

  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const url = `${API_BASE}${endpoint}`;
  let response: Response;

  try {
    response = await fetch(url, { ...options, headers });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Network request failed';
    throw new ApiException(0, {
      status: 0,
      detail: `Cannot connect to backend server at ${API_BASE}. Ensure the backend is running. (${errorMsg})`,
      title: 'Network Error',
    });
  }

  if (response.status === 204) {
    return {} as T;
  }

  let body: any = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      body = await response.json();
    } catch {
      body = null;
    }
  } else {
    body = await response.text();
  }

  if (!response.ok) {
    const errorData: ApiError =
      typeof body === 'object' && body !== null
        ? {
            status: response.status,
            detail: body.detail || body.message || 'Request failed',
            title: body.title || response.statusText,
            fieldErrors: body.fieldErrors,
          }
        : {
            status: response.status,
            detail: String(body || response.statusText),
            title: response.statusText,
          };
    throw new ApiException(response.status, errorData);
  }

  return body as T;
}

// Authentication APIs
export const apiLogin = async (username: string, password: string): Promise<AuthResponse> => {
  const data = await request<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  setToken(data.accessToken);
  setStoredUser(data.user);
  return data;
};

export const apiRegister = async (
  username: string,
  email: string,
  password: string,
  fullName: string
): Promise<UserProfile> => {
  return request<UserProfile>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password, fullName }),
  });
};

export const apiLogout = async (): Promise<void> => {
  try {
    await request<any>('/api/v1/auth/logout', { method: 'POST' });
  } finally {
    removeToken();
  }
};

// Account APIs
export const apiGetAccounts = async (): Promise<Account[]> => {
  return request<Account[]>('/api/v1/accounts');
};

export const apiGetAccountById = async (accountId: number): Promise<Account> => {
  return request<Account>(`/api/v1/accounts/${accountId}`);
};

// Financial Action APIs
export const apiDeposit = async (
  accountId: number,
  amount: number,
  description?: string
): Promise<TransactionResponse> => {
  return request<TransactionResponse>(`/api/v1/accounts/${accountId}/deposits`, {
    method: 'POST',
    body: JSON.stringify({ amount, description }),
  });
};

export const apiWithdraw = async (
  accountId: number,
  amount: number,
  description?: string
): Promise<TransactionResponse> => {
  return request<TransactionResponse>(`/api/v1/accounts/${accountId}/withdrawals`, {
    method: 'POST',
    body: JSON.stringify({ amount, description }),
  });
};

export const apiTransfer = async (
  sourceAccountId: number,
  destinationAccountId: number,
  amount: number,
  description?: string,
  idempotencyKey?: string
): Promise<TransferResponse> => {
  const key = idempotencyKey || crypto.randomUUID();
  return request<TransferResponse>('/api/v1/transfers', {
    method: 'POST',
    headers: {
      'Idempotency-Key': key,
    },
    body: JSON.stringify({
      sourceAccountId,
      destinationAccountId,
      amount,
      description,
    }),
  });
};

// Statement & Reconciliation APIs
export const apiGetStatement = async (accountId: number): Promise<AccountStatement> => {
  return request<AccountStatement>(`/api/v1/accounts/${accountId}/statement`);
};

export const apiReconcileAccount = async (accountId: number): Promise<AccountReconciliation> => {
  return request<AccountReconciliation>(`/api/v1/accounts/${accountId}/reconciliation`);
};

export const apiReconcileSystem = async (): Promise<SystemReconciliation> => {
  return request<SystemReconciliation>('/api/v1/auditor/system-reconciliation');
};

export const apiHealthCheck = async (): Promise<any> => {
  return request<any>('/api/v1/health');
};
