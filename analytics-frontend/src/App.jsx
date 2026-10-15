
import { useState } from "react";
import Dashboard from "./pages/Dashboard";
import Analytics from "./pages/Analytics";
import Events from "./pages/Events";
import "./App.css";

const AUTH_API_URL = "http://localhost:8080/api/auth/login";

function App() {
  const [activePage, setActivePage] = useState("Dashboard");

  const [token, setToken] = useState(
    () => localStorage.getItem("token") || ""
  );

  const [role, setRole] = useState(
    () => (localStorage.getItem("role") || "").toUpperCase()
  );

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loginError, setLoginError] = useState("");
  const [loggingIn, setLoggingIn] = useState(false);

  async function handleLogin(event) {
    event.preventDefault();
    setLoginError("");

    if (!email.trim() || !password) {
      setLoginError("Please enter your email and password.");
      return;
    }

    setLoggingIn(true);

    try {
      const response = await fetch(AUTH_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(
          data.message ||
            data.error ||
            `Login failed (${response.status}). Check your email and password.`
        );
      }

      if (!data.token) {
        throw new Error("The server did not return a login token.");
      }

      const accountRole = String(data.role || "")
        .toUpperCase()
        .replace(/^ROLE_/, "");

      if (!["ADMIN", "HR_ADMIN"].includes(accountRole)) {
        throw new Error(
          `Your account role is ${accountRole || "unknown"}. ADMIN or HR_ADMIN access is required.`
        );
      }

      localStorage.setItem("token", data.token);
      localStorage.setItem("role", accountRole);
      localStorage.setItem("email", data.email || email.trim());

      setToken(data.token);
      setRole(accountRole);
      setPassword("");
      setActivePage("Dashboard");
    } catch (error) {
      console.error("Analytics login failed:", error);

      setLoginError(
        error instanceof TypeError
          ? "Cannot connect to the backend. Check that Spring Boot is running on port 8080 and CORS allows this frontend."
          : error.message || "Login failed. Please try again."
      );
    } finally {
      setLoggingIn(false);
    }
  }

  function handleLogout() {
    localStorage.removeItem("token");
    localStorage.removeItem("role");
    localStorage.removeItem("email");

    setToken("");
    setRole("");
    setPassword("");
    setLoginError("");
    setActivePage("Dashboard");
  }

  if (!token) {
    return (
      <div className="auth-screen">
        <form className="auth-card" onSubmit={handleLogin}>
          <div className="auth-brand">
            <span className="brand-icon">A</span>
            <span>
              Analytics<span className="brand-light">Pro</span>
            </span>
          </div>

          <h1>Welcome back</h1>
          <p className="auth-description">
            Sign in with your MAISON Store account to access Analytics.
          </p>

          {loginError && (
            <div className="auth-error" role="alert">
              {loginError}
            </div>
          )}

          <label htmlFor="login-email">Email address</label>
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            placeholder="Enter your registered email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />

          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />

          <button
            className="auth-submit"
            type="submit"
            disabled={loggingIn}
          >
            {loggingIn ? "Signing in..." : "Sign in to Analytics"}
          </button>

          <p className="auth-note">
            An ADMIN or HR_ADMIN account is required.
          </p>
        </form>
      </div>
    );
  }

  const pages = {
    Dashboard: <Dashboard />,
    Analytics: <Analytics />,
    Events: <Events />,
  };

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">A</span>
          <span>
            Analytics<span className="brand-light">Pro</span>
          </span>
        </div>

        <p className="nav-label">WORKSPACE</p>

        {["Dashboard", "Analytics", "Events"].map((page) => (
          <button
            key={page}
            className={`nav-item ${activePage === page ? "active" : ""}`}
            onClick={() => setActivePage(page)}
          >
            <span>
              {page === "Dashboard" ? "▦" : page === "Analytics" ? "◷" : "≡"}
            </span>
            {page}
          </button>
        ))}

        <div className="sidebar-footer">
          <div className="avatar">AP</div>
          <div>
            <strong>Anjum Patel</strong>
            <small>{role}</small>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <p className="breadcrumb">Workspace / {activePage}</p>
            <h1>{activePage}</h1>
          </div>

          <div className="topbar-right">
            <span className="live-dot"></span>
            <span>Live dashboard</span>
            <button
              className="logout-button"
              type="button"
              onClick={handleLogout}
            >
              Logout
            </button>
          </div>
        </header>

        {pages[activePage]}
      </main>
    </div>
  );
}

export default App;
