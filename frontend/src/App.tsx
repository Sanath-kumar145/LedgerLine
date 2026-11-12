import { useState, useEffect } from 'react';
import type {
  UserProfile,
  Account,
  AccountStatement,
  TransferResponse,
  TransactionResponse,
  AccountReconciliation,
  SystemReconciliation,
} from './types';
import {
  apiLogin,
  apiRegister,
  apiLogout,
  apiGetAccounts,
  apiDeposit,
  apiWithdraw,
  apiTransfer,
  apiGetStatement,
  apiReconcileAccount,
  apiReconcileSystem,
  apiHealthCheck,
  getStoredUser,
  getToken,
  ApiException,
} from './api';
import './App.css';

type OpTab = 'transfer' | 'deposit' | 'withdraw' | 'statement' | 'reconciliation' | 'auditor';

export default function App() {
  // Authentication State
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(getStoredUser());
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [loginUsername, setLoginUsername] = useState('xfer_alice');
  const [loginPassword, setLoginPassword] = useState('Password123!');
  const [regFullName, setRegFullName] = useState('Alice Customer');
  const [regEmail, setRegEmail] = useState('alice@example.com');
  const [authLoading, setAuthLoading] = useState(false);

  // Application Data State
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<OpTab>('transfer');
  const [isLoading, setIsLoading] = useState(false);
  const [serverHealthy, setServerHealthy] = useState<boolean | null>(null);

  // Financial Operation Form States
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [destinationAccountId, setDestinationAccountId] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(crypto.randomUUID());

  // Operation Results and Statements
  const [statement, setStatement] = useState<AccountStatement | null>(null);
  const [accountRecon, setAccountRecon] = useState<AccountReconciliation | null>(null);
  const [systemRecon, setSystemRecon] = useState<SystemReconciliation | null>(null);
  const [lastTransferResult, setLastTransferResult] = useState<TransferResponse | null>(null);
  const [lastTxResult, setLastTxResult] = useState<TransactionResponse | null>(null);

  // Global Notification / Feedback
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Check backend health on mount
  useEffect(() => {
    apiHealthCheck()
      .then(() => setServerHealthy(true))
      .catch(() => setServerHealthy(false));
  }, []);

  // Fetch accounts when user logs in
  useEffect(() => {
    if (currentUser && getToken()) {
      loadAccounts();
    }
  }, [currentUser]);

  // Load statement or reconciliation when active tab or selected account changes
  useEffect(() => {
    if (selectedAccountId) {
      if (activeTab === 'statement') {
        loadStatement(selectedAccountId);
      } else if (activeTab === 'reconciliation') {
        loadAccountReconciliation(selectedAccountId);
      }
    }
    if (activeTab === 'auditor' && isAuditor()) {
      loadSystemReconciliation();
    }
  }, [selectedAccountId, activeTab]);

  const isAuditor = (): boolean => {
    return Boolean(currentUser?.roles.some((r) => r === 'ROLE_AUDITOR' || r === 'ROLE_ADMIN'));
  };

  const loadAccounts = async () => {
    try {
      setIsLoading(true);
      setErrorMessage(null);
      const data = await apiGetAccounts();
      setAccounts(data);
      if (data.length > 0 && !selectedAccountId) {
        setSelectedAccountId(data[0].id);
      }
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message || 'Failed to fetch accounts');
    } finally {
      setIsLoading(false);
    }
  };

  const loadStatement = async (accId: number) => {
    try {
      setIsLoading(true);
      const data = await apiGetStatement(accId);
      setStatement(data);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const loadAccountReconciliation = async (accId: number) => {
    try {
      setIsLoading(true);
      const data = await apiReconcileAccount(accId);
      setAccountRecon(data);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const loadSystemReconciliation = async () => {
    try {
      setIsLoading(true);
      const data = await apiReconcileSystem();
      setSystemRecon(data);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  // Auth Handlers
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setAuthLoading(true);
      setErrorMessage(null);
      const auth = await apiLogin(loginUsername, loginPassword);
      setCurrentUser(auth.user);
      setSuccessMessage(`Welcome back, ${auth.user.fullName}!`);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message || 'Login failed');
    } finally {
      setAuthLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setAuthLoading(true);
      setErrorMessage(null);
      await apiRegister(loginUsername, regEmail, loginPassword, regFullName);
      // Automatically log in
      const auth = await apiLogin(loginUsername, loginPassword);
      setCurrentUser(auth.user);
      setSuccessMessage(`Account created successfully! Welcome, ${auth.user.fullName}.`);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message || 'Registration failed');
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    await apiLogout();
    setCurrentUser(null);
    setAccounts([]);
    setSelectedAccountId(null);
    setStatement(null);
    setAccountRecon(null);
    setSystemRecon(null);
    setLastTransferResult(null);
    setLastTxResult(null);
    setSuccessMessage('Logged out safely.');
  };

  // Financial Operation Handlers
  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccountId || !amount) return;

    try {
      setIsLoading(true);
      setErrorMessage(null);
      setSuccessMessage(null);
      const res = await apiDeposit(
        selectedAccountId,
        parseFloat(amount),
        description.trim() || undefined
      );
      setLastTxResult(res);
      setSuccessMessage(`Deposit posted! Transaction Ref: ${res.transactionReference}. Customer balance updated to ₹${res.balanceAfter.toFixed(2)}.`);
      setAmount('');
      setDescription('');
      await loadAccounts();
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleWithdraw = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccountId || !amount) return;

    try {
      setIsLoading(true);
      setErrorMessage(null);
      setSuccessMessage(null);
      const res = await apiWithdraw(
        selectedAccountId,
        parseFloat(amount),
        description.trim() || undefined
      );
      setLastTxResult(res);
      setSuccessMessage(`Withdrawal posted! Transaction Ref: ${res.transactionReference}. Remaining balance: ₹${res.balanceAfter.toFixed(2)}.`);
      setAmount('');
      setDescription('');
      await loadAccounts();
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccountId || !destinationAccountId || !amount) return;

    try {
      setIsLoading(true);
      setErrorMessage(null);
      setSuccessMessage(null);

      const res = await apiTransfer(
        selectedAccountId,
        parseInt(destinationAccountId),
        parseFloat(amount),
        description.trim() || undefined,
        idempotencyKey
      );

      setLastTransferResult(res);

      if (res.replayed) {
        setSuccessMessage(`[IDEMPOTENT REPLAY]: Request was previously processed (HTTP 200). Stored result returned with zero duplicate funds movement! (Ref: ${res.transactionReference})`);
      } else {
        setSuccessMessage(`Transfer successfully posted (HTTP 201)! Ref: ${res.transactionReference}. Source balance: ₹${res.sourceBalanceAfter.toFixed(2)}, Destination balance: ₹${res.destinationBalanceAfter.toFixed(2)}.`);
        // Generate new key for next fresh transfer
        setIdempotencyKey(crypto.randomUUID());
      }

      await loadAccounts();
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  // Idempotency demo helpers
  const handleReplayTransfer = async () => {
    if (!lastTransferResult) return;
    try {
      setIsLoading(true);
      setErrorMessage(null);
      setSuccessMessage(null);
      const res = await apiTransfer(
        lastTransferResult.sourceAccountId,
        lastTransferResult.destinationAccountId,
        lastTransferResult.amount,
        lastTransferResult.description,
        lastTransferResult.idempotencyKey
      );
      setLastTransferResult(res);
      setSuccessMessage(`[IDEMPOTENT REPLAY VERIFIED]: Repeated transfer with key '${lastTransferResult.idempotencyKey.slice(0, 16)}...' returned identical transaction ref '${res.transactionReference}' without moving funds again! (replayed = ${res.replayed})`);
    } catch (err: any) {
      setErrorMessage(err.data?.detail || err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleConflictTransfer = async () => {
    if (!lastTransferResult) return;
    try {
      setIsLoading(true);
      setErrorMessage(null);
      setSuccessMessage(null);
      // Deliberately alter the amount by +₹100 to cause conflict with identical key
      const alteredAmount = lastTransferResult.amount + 100.0;
      await apiTransfer(
        lastTransferResult.sourceAccountId,
        lastTransferResult.destinationAccountId,
        alteredAmount,
        lastTransferResult.description,
        lastTransferResult.idempotencyKey
      );
    } catch (err: any) {
      if (err instanceof ApiException && err.status === 409) {
        setSuccessMessage(`[CONFLICT DETECTED - HTTP 409]: Backend correctly rejected payload alteration on existing key! Error details: "${err.data.detail}"`);
      } else {
        setErrorMessage(err.data?.detail || err.message);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);

  // Render Unauthenticated View
  if (!currentUser) {
    return (
      <div className="app-wrapper">
        <header className="navbar">
          <div className="brand-section">
            <div className="brand-logo">LL</div>
            <div className="brand-title">
              LedgerLine
              <span className="brand-badge">Financial Integrity Engine</span>
            </div>
          </div>
          <div className="user-nav-section">
            <div className="health-pill">
              <span className="health-dot" style={{ background: serverHealthy ? '#10b981' : '#ef4444' }}></span>
              {serverHealthy ? 'Backend: UP' : 'Backend: Connecting...'}
            </div>
          </div>
        </header>

        <main className="main-container">
          <div className="auth-container">
            <div className="auth-header">
              <h1>{authMode === 'login' ? 'Sign In to LedgerLine' : 'Create an Account'}</h1>
              <p>Double-Entry Ledger with Provable Mathematical Invariants</p>
            </div>

            <div className="auth-tabs">
              <button
                type="button"
                className={`auth-tab-btn ${authMode === 'login' ? 'active' : ''}`}
                onClick={() => setAuthMode('login')}
              >
                Sign In
              </button>
              <button
                type="button"
                className={`auth-tab-btn ${authMode === 'register' ? 'active' : ''}`}
                onClick={() => setAuthMode('register')}
              >
                Register
              </button>
            </div>

            {errorMessage && (
              <div className="alert error">
                <div className="alert-title">Authentication Error</div>
                <div>{errorMessage}</div>
              </div>
            )}
            {successMessage && (
              <div className="alert success">
                <div className="alert-title">Success</div>
                <div>{successMessage}</div>
              </div>
            )}

            <form onSubmit={authMode === 'login' ? handleLogin : handleRegister}>
              {authMode === 'register' && (
                <>
                  <div className="form-group">
                    <label className="form-label">Full Name</label>
                    <input
                      type="text"
                      className="form-input"
                      value={regFullName}
                      onChange={(e) => setRegFullName(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Email Address</label>
                    <input
                      type="email"
                      className="form-input"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      required
                    />
                  </div>
                </>
              )}

              <div className="form-group">
                <label className="form-label">Username</label>
                <input
                  type="text"
                  className="form-input"
                  value={loginUsername}
                  onChange={(e) => setLoginUsername(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Password</label>
                <input
                  type="password"
                  className="form-input"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="btn-primary" disabled={authLoading}>
                {authLoading ? 'Authenticating...' : authMode === 'login' ? 'Sign In' : 'Create Account'}
              </button>
            </form>

            <div className="demo-credentials-box">
              <div className="demo-title">Quick Demo Logins</div>
              <div className="demo-btns">
                <button
                  type="button"
                  className="btn-demo"
                  onClick={() => {
                    setLoginUsername('demo_alice');
                    setLoginPassword('DemoPassword123!');
                    setAuthMode('login');
                  }}
                >
                  Demo User
                </button>
                <button
                  type="button"
                  className="btn-demo"
                  onClick={() => {
                    setLoginUsername('auditor_officer');
                    setLoginPassword('AuditorPassword123!');
                    setAuthMode('login');
                  }}
                >
                  Auditor Officer
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    );
  }

  // Render Authenticated Dashboard
  return (
    <div className="app-wrapper">
      <header className="navbar">
        <div className="brand-section">
          <div className="brand-logo">LL</div>
          <div className="brand-title">
            LedgerLine
            <span className="brand-badge">Double-Entry Core</span>
          </div>
        </div>

        <div className="user-nav-section">
          <div className="health-pill">
            <span className="health-dot"></span>
            Online
          </div>

          <div className="user-pill">
            <span>{currentUser.fullName}</span>
            {currentUser.roles.map((r) => (
              <span key={r} className="role-tag">
                {r.replace('ROLE_', '')}
              </span>
            ))}
          </div>

          <button type="button" className="btn-logout" onClick={handleLogout}>
            Sign Out
          </button>
        </div>
      </header>

      <main className="main-container">
        <div className="dashboard-grid">
          {/* Notifications */}
          {errorMessage && (
            <div className="alert error">
              <div className="alert-title">Operation Error</div>
              <div>{errorMessage}</div>
            </div>
          )}
          {successMessage && (
            <div className="alert success">
              <div className="alert-title">Success</div>
              <div>{successMessage}</div>
            </div>
          )}

          {/* Account Overview Cards */}
          <section className="accounts-section">
            <div className="section-header">
              <div className="section-title">Your Accounts ({accounts.length})</div>
              <button
                type="button"
                className="btn-preset"
                onClick={loadAccounts}
                disabled={isLoading}
              >
                Refresh Balances
              </button>
            </div>

            {accounts.length === 0 ? (
              <div className="empty-state">
                No accounts found. Create a deposit or register an account.
              </div>
            ) : (
              <div className="accounts-cards-row">
                {accounts.map((acc) => (
                  <div
                    key={acc.id}
                    className={`account-card ${selectedAccountId === acc.id ? 'selected' : ''}`}
                    onClick={() => setSelectedAccountId(acc.id)}
                  >
                    <div className="card-top">
                      <span className="account-type-tag">{acc.accountType}</span>
                      <span className={`status-badge ${acc.status.toLowerCase()}`}>
                        {acc.status}
                      </span>
                    </div>
                    <div className="account-number">{acc.accountNumber} (ID: {acc.id})</div>
                    <div className="account-balance">
                      ₹{acc.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </div>
                    <div className="helper-text">{acc.currency} • Double-entry verified</div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Operations Tab Bar */}
          <div className="operation-tabs">
            <button
              type="button"
              className={`op-tab-btn ${activeTab === 'transfer' ? 'active' : ''}`}
              onClick={() => setActiveTab('transfer')}
            >
              🔄 Internal Transfer
            </button>
            <button
              type="button"
              className={`op-tab-btn ${activeTab === 'deposit' ? 'active' : ''}`}
              onClick={() => setActiveTab('deposit')}
            >
              💳 External Deposit
            </button>
            <button
              type="button"
              className={`op-tab-btn ${activeTab === 'withdraw' ? 'active' : ''}`}
              onClick={() => setActiveTab('withdraw')}
            >
              💸 External Withdrawal
            </button>
            <button
              type="button"
              className={`op-tab-btn ${activeTab === 'statement' ? 'active' : ''}`}
              onClick={() => setActiveTab('statement')}
            >
              📜 Account Statement
            </button>
            <button
              type="button"
              className={`op-tab-btn ${activeTab === 'reconciliation' ? 'active' : ''}`}
              onClick={() => setActiveTab('reconciliation')}
            >
              🔍 Live Math Audit
            </button>
            {isAuditor() && (
              <button
                type="button"
                className={`op-tab-btn auditor-tab ${activeTab === 'auditor' ? 'active' : ''}`}
                onClick={() => setActiveTab('auditor')}
              >
                🛡️ System Auditor
              </button>
            )}
          </div>

          {/* Operation Panel */}
          <div className="op-panel">
            {/* Transfer Tab */}
            {activeTab === 'transfer' && (
              <div className="op-form">
                <div className="panel-header">
                  <h2>Atomic Inter-Account Transfer</h2>
                  <p>
                    Moves money between accounts with balanced Debit & Credit ledger entries,
                    deterministic row locking order, and database-enforced idempotency.
                  </p>
                </div>

                <form onSubmit={handleTransfer}>
                  <div className="form-group">
                    <label className="form-label">Source Account (Sender)</label>
                    <select
                      className="form-input"
                      value={selectedAccountId || ''}
                      onChange={(e) => setSelectedAccountId(parseInt(e.target.value))}
                      required
                    >
                      {accounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.accountNumber} — Available: ₹{a.balance.toFixed(2)} ({a.accountType})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Destination Account ID (Recipient)</label>
                    <input
                      type="number"
                      className="form-input"
                      placeholder="Enter recipient Account ID (e.g. 2, 4)"
                      value={destinationAccountId}
                      onChange={(e) => setDestinationAccountId(e.target.value)}
                      required
                    />
                    <div className="helper-text">
                      Sender and recipient must be distinct accounts.
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Transfer Amount (INR)</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="form-input"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      required
                    />
                    <div className="preset-row">
                      <button type="button" className="btn-preset" onClick={() => setAmount('100.00')}>
                        +₹100
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('500.00')}>
                        +₹500
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('1000.00')}>
                        +₹1,000
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Description (Optional)</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Invoice payment, rent, gift"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>

                  {/* Idempotency Controls */}
                  <div className="idemp-box">
                    <div className="idemp-row">
                      <span className="idemp-label">Idempotency-Key Header</span>
                      <button
                        type="button"
                        className="btn-idemp"
                        onClick={() => setIdempotencyKey(crypto.randomUUID())}
                      >
                        Generate New Key
                      </button>
                    </div>
                    <div className="idemp-key-text">{idempotencyKey}</div>
                    <div className="helper-text">
                      Protects against accidental double submissions or network retries.
                    </div>

                    {lastTransferResult && (
                      <div className="idemp-actions">
                        <button
                          type="button"
                          className="btn-idemp"
                          onClick={handleReplayTransfer}
                          disabled={isLoading}
                        >
                          🧪 Replay Last Request (Zero Duplicate Debits)
                        </button>
                        <button
                          type="button"
                          className="btn-idemp"
                          onClick={handleConflictTransfer}
                          disabled={isLoading}
                        >
                          ⚠️ Test Conflict (Altered Payload + Same Key)
                        </button>
                      </div>
                    )}
                  </div>

                  <button type="submit" className="btn-primary" disabled={isLoading}>
                    {isLoading ? 'Posting Atomic Transfer...' : 'Confirm & Post Transfer'}
                  </button>
                </form>
              </div>
            )}

            {/* Deposit Tab */}
            {activeTab === 'deposit' && (
              <div className="op-form">
                <div className="panel-header">
                  <h2>External Asset Deposit</h2>
                  <p>
                    Simulates an external asset inflow. In double-entry bookkeeping, external
                    deposits debit the System Clearing Account (`SYS_CLEARING_INR`) and credit the
                    customer account.
                  </p>
                </div>

                <form onSubmit={handleDeposit}>
                  <div className="form-group">
                    <label className="form-label">Deposit Target Account</label>
                    <select
                      className="form-input"
                      value={selectedAccountId || ''}
                      onChange={(e) => setSelectedAccountId(parseInt(e.target.value))}
                      required
                    >
                      {accounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.accountNumber} — Balance: ₹{a.balance.toFixed(2)}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Amount (INR)</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="form-input"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      required
                    />
                    <div className="preset-row">
                      <button type="button" className="btn-preset" onClick={() => setAmount('500.00')}>
                        +₹500
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('1000.00')}>
                        +₹1,000
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('5000.00')}>
                        +₹5,000
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Description (Optional)</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Salary deposit, cash replenishment"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>

                  <button type="submit" className="btn-primary" disabled={isLoading}>
                    {isLoading ? 'Executing Deposit...' : 'Deposit Funds'}
                  </button>

                  {lastTxResult && (
                    <div className="idemp-box" style={{ marginTop: '1rem' }}>
                      <div className="idemp-label">Last Operation Receipt</div>
                      <div className="mono-cell" style={{ color: '#34d399', fontWeight: 600 }}>
                        {lastTxResult.transactionReference}
                      </div>
                      <div className="helper-text">
                        {lastTxResult.transactionType} of ₹{lastTxResult.amount.toFixed(2)} posted at{' '}
                        {new Date(lastTxResult.completedAt).toLocaleTimeString()} • Balance After: ₹{lastTxResult.balanceAfter.toFixed(2)}
                      </div>
                    </div>
                  )}
                </form>
              </div>
            )}

            {/* Withdrawal Tab */}
            {activeTab === 'withdraw' && (
              <div className="op-form">
                <div className="panel-header">
                  <h2>External Asset Withdrawal</h2>
                  <p>
                    Debits the customer account and credits the System Clearing Account.
                    Protected by exclusive pessimistic row locking (`SELECT ... FOR UPDATE`)
                    and authoritative fresh balance checks. Overdrafts are impossible.
                  </p>
                </div>

                <form onSubmit={handleWithdraw}>
                  <div className="form-group">
                    <label className="form-label">Withdraw From Account</label>
                    <select
                      className="form-input"
                      value={selectedAccountId || ''}
                      onChange={(e) => setSelectedAccountId(parseInt(e.target.value))}
                      required
                    >
                      {accounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.accountNumber} — Available: ₹{a.balance.toFixed(2)}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Amount (INR)</label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      className="form-input"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      required
                    />
                    <div className="preset-row">
                      <button type="button" className="btn-preset" onClick={() => setAmount('200.00')}>
                        ₹200
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('500.00')}>
                        ₹500
                      </button>
                      <button type="button" className="btn-preset" onClick={() => setAmount('1000.00')}>
                        ₹1,000
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Description (Optional)</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. ATM cash withdrawal, bill payment"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>

                  <button type="submit" className="btn-primary" disabled={isLoading}>
                    {isLoading ? 'Processing Withdrawal...' : 'Withdraw Funds'}
                  </button>

                  {lastTxResult && (
                    <div className="idemp-box" style={{ marginTop: '1rem' }}>
                      <div className="idemp-label">Last Operation Receipt</div>
                      <div className="mono-cell" style={{ color: '#34d399', fontWeight: 600 }}>
                        {lastTxResult.transactionReference}
                      </div>
                      <div className="helper-text">
                        {lastTxResult.transactionType} of ₹{lastTxResult.amount.toFixed(2)} posted at{' '}
                        {new Date(lastTxResult.completedAt).toLocaleTimeString()} • Balance After: ₹{lastTxResult.balanceAfter.toFixed(2)}
                      </div>
                    </div>
                  )}
                </form>
              </div>
            )}

            {/* Statement Tab */}
            {activeTab === 'statement' && (
              <div>
                <div className="panel-header">
                  <h2>Official Account Statement & Ledger Journal</h2>
                  <p>
                    Every entry represents a verified, append-only posting in the double-entry
                    ledger. Materialized balances equal the sum of credits minus debits.
                  </p>
                </div>

                {selectedAccount && (
                  <div className="helper-text" style={{ marginBottom: '1rem' }}>
                    Showing statement for <strong>{selectedAccount.accountNumber}</strong> • Current Balance: ₹{selectedAccount.balance.toFixed(2)}
                  </div>
                )}

                {statement && statement.entries.length > 0 ? (
                  <div className="statement-table-wrapper">
                    <table className="statement-table">
                      <thead>
                        <tr>
                          <th>Date & Time (UTC)</th>
                          <th>Type</th>
                          <th>Amount</th>
                          <th>Balance After</th>
                          <th>Transaction Reference</th>
                          <th>Description</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statement.entries.map((entry) => (
                          <tr key={entry.id}>
                            <td className="mono-cell">
                              {new Date(entry.postedAt).toLocaleString()}
                            </td>
                            <td>
                              <span
                                className={`entry-badge ${
                                  entry.entryType === 'CREDIT' ? 'credit' : 'debit'
                                }`}
                              >
                                {entry.entryType}
                              </span>
                            </td>
                            <td style={{ fontWeight: 700 }}>
                              {entry.entryType === 'CREDIT' ? '+' : '-'}₹{entry.amount.toFixed(2)}
                            </td>
                            <td className="mono-cell">₹{entry.balanceAfter.toFixed(2)}</td>
                            <td className="mono-cell">{entry.transactionReference}</td>
                            <td>{entry.description || '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="empty-state">No transaction entries found for this account.</div>
                )}
              </div>
            )}

            {/* Reconciliation Tab */}
            {activeTab === 'reconciliation' && (
              <div>
                <div className="panel-header">
                  <h2>Live Mathematical Reconciliation</h2>
                  <p>
                    Independent audit query comparing the stored account balance against the
                    sum of all posted double entries:
                    <br />
                    <code>Balance = Σ Credits - Σ Debits</code>
                  </p>
                </div>

                {accountRecon ? (
                  <div>
                    <div className="recon-grid">
                      <div className="recon-stat-card">
                        <div className="recon-stat-title">Materialized Balance</div>
                        <div className="recon-stat-val">
                          ₹{accountRecon.materializedBalance.toFixed(2)}
                        </div>
                      </div>

                      <div className="recon-stat-card">
                        <div className="recon-stat-title">Computed Ledger Sum</div>
                        <div className="recon-stat-val">
                          ₹{accountRecon.computedLedgerBalance.toFixed(2)}
                        </div>
                        <div className="helper-text">
                          Credits: +₹{accountRecon.totalCredits.toFixed(2)} | Debits: -₹{accountRecon.totalDebits.toFixed(2)}
                        </div>
                      </div>

                      <div className="recon-stat-card">
                        <div className="recon-stat-title">Discrepancy (Delta)</div>
                        <div
                          className={`recon-stat-val ${
                            accountRecon.discrepancy === 0 ? 'zero' : 'discrepancy'
                          }`}
                        >
                          ₹{accountRecon.discrepancy.toFixed(2)}
                        </div>
                      </div>
                    </div>

                    <div
                      className={`recon-status-banner ${
                        accountRecon.isReconciled ? 'balanced' : 'corrupted'
                      }`}
                    >
                      <div style={{ fontSize: '1.5rem' }}>
                        {accountRecon.isReconciled ? '✅' : '🚨'}
                      </div>
                      <div>
                        <strong>
                          {accountRecon.isReconciled
                            ? 'Mathematical Integrity Verified'
                            : 'Integrity Discrepancy Flagged'}
                        </strong>
                        <div style={{ fontSize: '0.825rem' }}>
                          Verified at {new Date(accountRecon.verifiedAt).toLocaleTimeString()}.
                          {accountRecon.isReconciled
                            ? ' Account balance strictly equals the integral of all ledger entries.'
                            : ' Alert: Stored balance does not match the ledger entries!'}
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="empty-state">Loading reconciliation data...</div>
                )}
              </div>
            )}

            {/* Auditor View Tab */}
            {activeTab === 'auditor' && isAuditor() && (
              <div>
                <div className="panel-header">
                  <h2>System-Wide Conservation of Money Audit</h2>
                  <p>
                    Auditor inspection proving macro-integrity: all posted debits in the entire
                    financial system must strictly equal all posted credits (including system
                    clearing accounts).
                  </p>
                </div>

                {systemRecon ? (
                  <div>
                    <div className="recon-grid">
                      <div className="recon-stat-card">
                        <div className="recon-stat-title">System Total Debits</div>
                        <div className="recon-stat-val" style={{ color: '#f87171' }}>
                          ₹{systemRecon.systemTotalDebits.toFixed(2)}
                        </div>
                      </div>

                      <div className="recon-stat-card">
                        <div className="recon-stat-title">System Total Credits</div>
                        <div className="recon-stat-val" style={{ color: '#34d399' }}>
                          ₹{systemRecon.systemTotalCredits.toFixed(2)}
                        </div>
                      </div>

                      <div className="recon-stat-card">
                        <div className="recon-stat-title">System Imbalance Delta</div>
                        <div
                          className={`recon-stat-val ${
                            systemRecon.systemDelta === 0 ? 'zero' : 'discrepancy'
                          }`}
                        >
                          ₹{systemRecon.systemDelta.toFixed(2)}
                        </div>
                      </div>
                    </div>

                    <div
                      className={`recon-status-banner ${
                        systemRecon.integrityStatus === 'SYSTEM_BALANCED' ? 'balanced' : 'corrupted'
                      }`}
                    >
                      <div style={{ fontSize: '1.5rem' }}>
                        {systemRecon.integrityStatus === 'SYSTEM_BALANCED' ? '🛡️' : '⚠️'}
                      </div>
                      <div>
                        <strong>System Status: {systemRecon.integrityStatus}</strong>
                        <div style={{ fontSize: '0.825rem' }}>
                          Audited {systemRecon.totalAccountsAudited} accounts,{' '}
                          {systemRecon.totalTransactionsAudited} transactions, and{' '}
                          {systemRecon.totalEntriesAudited} double entries. Unbalanced transactions:{' '}
                          {systemRecon.unbalancedTransactionCount}.
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="empty-state">Loading system-wide audit data...</div>
                )}
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
