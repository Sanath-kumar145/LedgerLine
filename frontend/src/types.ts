export interface UserProfile {
  id: number;
  username: string;
  email: string;
  fullName: string;
  status: string;
  roles: string[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: UserProfile;
}

export interface Account {
  id: number;
  accountNumber: string;
  userId: number;
  accountType: 'CHECKING' | 'SAVINGS' | 'SYSTEM_CLEARING';
  currency: string;
  balance: number;
  status: 'ACTIVE' | 'FROZEN' | 'SUSPENDED' | 'CLOSED';
  createdAt?: string;
}

export interface StatementEntry {
  id: number;
  transactionReference: string;
  entryType: 'DEBIT' | 'CREDIT';
  amount: number;
  balanceAfter: number;
  postedAt: string;
  description: string;
}

export interface AccountStatement {
  accountId: number;
  accountNumber: string;
  currency: string;
  currentBalance: number;
  totalEntries: number;
  generatedAt: string;
  entries: StatementEntry[];
}

export interface TransactionResponse {
  transactionReference: string;
  accountId: number;
  accountNumber: string;
  transactionType: string;
  amount: number;
  currency: string;
  balanceAfter: number;
  status: string;
  description: string;
  completedAt: string;
}

export interface TransferResponse {
  transactionReference: string;
  transactionType: string;
  sourceAccountId: number;
  destinationAccountId: number;
  amount: number;
  currency: string;
  sourceBalanceAfter: number;
  destinationBalanceAfter: number;
  status: string;
  description: string;
  idempotencyKey: string;
  replayed: boolean;
  completedAt: string;
  entries: {
    id: number;
    transactionReference: string;
    entryType: 'DEBIT' | 'CREDIT';
    amount: number;
    balanceAfter: number;
    postedAt: string;
    description: string;
  }[];
}

export interface AccountReconciliation {
  accountId: number;
  accountNumber: string;
  currency: string;
  materializedBalance: number;
  totalCredits: number;
  totalDebits: number;
  computedLedgerBalance: number;
  discrepancy: number;
  isReconciled: boolean;
  verifiedAt: string;
}

export interface SystemReconciliation {
  verifiedAt: string;
  integrityStatus: string;
  systemTotalDebits: number;
  systemTotalCredits: number;
  systemDelta: number;
  unbalancedTransactionCount: number;
  totalAccountsAudited: number;
  totalTransactionsAudited: number;
  totalEntriesAudited: number;
  unbalancedTransactionIds: number[];
}

export interface ApiError {
  type?: string;
  title?: string;
  status: number;
  detail: string;
  instance?: string;
  fieldErrors?: Record<string, string>;
}

