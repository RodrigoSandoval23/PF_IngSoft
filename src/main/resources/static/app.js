const STORAGE_TOKEN_KEY = "solidaria_auth_token";
const STORAGE_USER_KEY = "solidaria_auth_user";

let currentToken = null;
let currentUser = null;

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
});

function initAuth() {
  const storedToken = localStorage.getItem(STORAGE_TOKEN_KEY);
  const storedUser = localStorage.getItem(STORAGE_USER_KEY);

  if (storedToken && storedUser) {
    try {
      currentToken = storedToken;
      currentUser = JSON.parse(storedUser);
      showAppView();
      validateSession();
    } catch (e) {
      logout(false);
    }
  } else {
    showLoginView();
  }
}

function showLoginView() {
  const loginView = document.getElementById("login-view");
  const appView = document.getElementById("app-view");
  if (loginView) loginView.classList.remove("hidden");
  if (appView) appView.classList.add("hidden");
}

function showAppView() {
  const loginView = document.getElementById("login-view");
  const appView = document.getElementById("app-view");
  if (loginView) loginView.classList.add("hidden");
  if (appView) appView.classList.remove("hidden");

  updateSessionIcon();
}

function updateSessionIcon() {
  if (!currentUser) return;

  const isAdmin = currentUser.role === "ADMIN";

  const cardUserName = document.getElementById("card-user-name");
  const cardUserEmail = document.getElementById("card-user-email");
  const cardUserRfc = document.getElementById("card-user-rfc");
  const cardUserEntity = document.getElementById("card-user-entity");
  const cardUserRole = document.getElementById("card-user-role");
  const cardUserStatus = document.getElementById("card-user-status");
  const btnCardAdmin = document.getElementById("btn-card-admin");

  if (cardUserName) cardUserName.textContent = currentUser.name || "-";
  if (cardUserEmail) cardUserEmail.textContent = currentUser.email || "-";
  if (cardUserRfc) cardUserRfc.textContent = currentUser.rfc || "Sin RFC";
  if (cardUserEntity) cardUserEntity.textContent = currentUser.entity_type || (isAdmin ? "ADMINISTRACIÓN" : "ENTIDAD REGISTRADA");
  if (cardUserRole) cardUserRole.textContent = currentUser.role || "DONANTE";
  if (cardUserStatus) {
    cardUserStatus.textContent = currentUser.status || "ACTIVO";
    cardUserStatus.className = currentUser.status === "ACTIVO" ? "status-pill status-active" : "status-pill status-pending";
  }

  if (btnCardAdmin) {
    if (isAdmin) btnCardAdmin.classList.remove("hidden");
    else btnCardAdmin.classList.add("hidden");
  }
}

function switchAuthTab(tab) {
  const btnLogin = document.getElementById("tab-login-btn");
  const btnRegister = document.getElementById("tab-register-btn");
  const formLogin = document.getElementById("form-login");
  const formRegister = document.getElementById("form-register");
  const errorMsg = document.getElementById("auth-error-msg");
  const successMsg = document.getElementById("auth-success-msg");

  if (errorMsg) errorMsg.classList.add("hidden");
  if (successMsg) successMsg.classList.add("hidden");

  if (tab === "login") {
    if (btnLogin) btnLogin.classList.add("active");
    if (btnRegister) btnRegister.classList.remove("active");
    if (formLogin) formLogin.classList.remove("hidden");
    if (formRegister) formRegister.classList.add("hidden");
  } else {
    if (btnRegister) btnRegister.classList.add("active");
    if (btnLogin) btnLogin.classList.remove("active");
    if (formRegister) formRegister.classList.remove("hidden");
    if (formLogin) formLogin.classList.add("hidden");
  }
}

function updateEntitySelection(radio) {
  document.querySelectorAll(".entity-radio-card").forEach(c => c.classList.remove("active"));
  if (radio && radio.closest(".entity-radio-card")) {
    radio.closest(".entity-radio-card").classList.add("active");
  }
}

function fillDemoCredentials() {
  document.getElementById("login-email").value = "demo@donaciones.org";
  document.getElementById("login-password").value = "demo1234";
}

async function handleLoginSubmit(event) {
  event.preventDefault();
  const email = document.getElementById("login-email").value.trim();
  const password = document.getElementById("login-password").value;
  const errorMsg = document.getElementById("auth-error-msg");
  const submitBtn = document.getElementById("btn-submit-login");

  if (errorMsg) errorMsg.classList.add("hidden");
  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    const data = await res.json();

    if (res.status === 429) {
      errorMsg.textContent = data.detail || "Demasiados intentos fallidos. Acceso temporalmente bloqueado.";
      errorMsg.classList.remove("hidden");
      showToast("Límite de intentos superado. Intenta más tarde.", "error");
      return;
    }

    if (res.status === 403) {
      errorMsg.textContent = data.detail || "Tu cuenta está pendiente de validación.";
      errorMsg.classList.remove("hidden");
      showPendingAlertModal();
      return;
    }

    if (!res.ok) {
      errorMsg.textContent = data.detail || "Credenciales inválidas.";
      errorMsg.classList.remove("hidden");
      return;
    }

    currentToken = data.access_token;
    currentUser = data.user;
    localStorage.setItem(STORAGE_TOKEN_KEY, currentToken);
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));

    showAppView();
    showToast("Usuario registrado", "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error de conexión con el servidor.";
      errorMsg.classList.remove("hidden");
    }
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

async function handleRegisterSubmit(event) {
  event.preventDefault();
  const rfc = document.getElementById("reg-rfc").value.trim().toUpperCase();
  const legalName = document.getElementById("reg-legal-name").value.trim();
  const entityTypeRadio = document.querySelector("input[name='reg_entity_type']:checked");
  const entityType = entityTypeRadio ? entityTypeRadio.value : "ORGANIZACION_SOCIAL";

  const name = document.getElementById("reg-name").value.trim();
  const role = document.getElementById("reg-role").value;
  const email = document.getElementById("reg-email").value.trim().toLowerCase();
  const password = document.getElementById("reg-password").value;

  const errorMsg = document.getElementById("auth-error-msg");
  const successMsg = document.getElementById("auth-success-msg");
  const submitBtn = document.getElementById("btn-submit-register");

  if (errorMsg) errorMsg.classList.add("hidden");
  if (successMsg) successMsg.classList.add("hidden");

  if (rfc.length < 12 || rfc.length > 13) {
    errorMsg.textContent = "El RFC debe tener entre 12 y 13 caracteres.";
    errorMsg.classList.remove("hidden");
    return;
  }

  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name,
        email,
        password,
        role,
        rfc,
        legal_name: legalName,
        entity_type: entityType
      }),
    });

    const data = await res.json();

    if (!res.ok) {
      errorMsg.textContent = data.detail || "Error en el registro de la entidad.";
      errorMsg.classList.remove("hidden");
      return;
    }

    document.getElementById("form-register").reset();
    if (successMsg) {
      successMsg.textContent = "Registrado exitosamente";
      successMsg.classList.remove("hidden");
    }
    showToast("Registrado exitosamente", "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error de conexión con el servidor.";
      errorMsg.classList.remove("hidden");
    }
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

function logout(notify = true) {
  currentToken = null;
  currentUser = null;
  localStorage.removeItem(STORAGE_TOKEN_KEY);
  localStorage.removeItem(STORAGE_USER_KEY);

  showLoginView();
  if (notify) {
    showToast("Sesión cerrada.", "info");
  }
}

function showPendingAlertModal() {
  const modal = document.getElementById("pending-alert-modal");
  if (modal) modal.classList.remove("hidden");
}

function closePendingAlertModal() {
  const modal = document.getElementById("pending-alert-modal");
  if (modal) modal.classList.add("hidden");
}

async function apiFetch(url, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (currentToken) {
    headers["Authorization"] = `Bearer ${currentToken}`;
  }

  const response = await fetch(url, { ...options, headers });

  if (response.status === 401 && currentToken) {
    showToast("Tu sesión ha expirado. Ingresa nuevamente.", "error");
    logout(false);
  } else if (response.status === 403) {
    const data = await response.clone().json().catch(() => ({}));
    showToast(data.detail || "Acceso restringido.", "error");
  }

  return response;
}

async function validateSession() {
  try {
    const res = await apiFetch("/api/auth/me");
    if (res.ok) {
      currentUser = await res.json();
      localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));
      updateSessionIcon();
    } else {
      logout(false);
    }
  } catch (err) {
    console.warn("Error al validar sesión:", err);
  }
}

function openAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  if (modal) modal.classList.remove("hidden");
  switchAdminSubTab("pending");
}

function closeAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  if (modal) modal.classList.add("hidden");
}

function switchAdminSubTab(tab) {
  const tabs = ["pending", "users", "donations", "audit"];
  tabs.forEach(t => {
    const btn = document.getElementById(`tab-adm-${t}`);
    const panel = document.getElementById(`admin-${t}-panel`);
    if (btn) btn.classList.toggle("active", t === tab);
    if (panel) panel.classList.toggle("hidden", t !== tab);
  });

  if (tab === "pending") loadAdminPending();
  if (tab === "users") loadAdminUsers();
  if (tab === "donations") loadAdminDonations();
  if (tab === "audit") loadAdminAudit();
}

async function loadAdminPending() {
  const container = document.getElementById("admin-pending-container");
  const countEl = document.getElementById("admin-pending-count");
  if (!container) return;

  try {
    const res = await apiFetch("/api/admin/pending");
    if (!res.ok) return;
    const users = await res.json();
    if (countEl) countEl.textContent = users.length;

    if (users.length === 0) {
      container.innerHTML = '<p class="text-center p-3 text-muted">No hay solicitudes pendientes de validación.</p>';
      return;
    }

    let html = `<table class="glass-table">
      <thead>
        <tr>
          <th>ID</th><th>Nombre</th><th>Email</th><th>RFC</th><th>Razón Social</th><th>Entidad</th><th>Acción</th>
        </tr>
      </thead><tbody>`;

    users.forEach(u => {
      html += `<tr>
        <td>#${u.id}</td>
        <td><strong>${escapeHtml(u.name)}</strong></td>
        <td>${escapeHtml(u.email)}</td>
        <td><code class="font-mono text-emerald">${escapeHtml(u.rfc || "-")}</code></td>
        <td>${escapeHtml(u.legal_name || "-")}</td>
        <td><span class="pill-role">${escapeHtml(u.entity_type || "-")}</span></td>
        <td>
          <button class="btn-action-sm btn-approve" onclick="approveUser(${u.id})">Aprobar</button>
          <button class="btn-action-sm btn-reject" onclick="rejectUser(${u.id})">Rechazar</button>
        </td>
      </tr>`;
    });

    html += "</tbody></table>";
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = '<p class="text-center p-3 text-muted">Error al cargar solicitudes.</p>';
  }
}

async function loadAdminUsers() {
  const container = document.getElementById("admin-users-container");
  const countEl = document.getElementById("admin-users-count");
  if (!container) return;

  try {
    const res = await apiFetch("/api/admin/users");
    if (!res.ok) return;
    const users = await res.json();
    if (countEl) countEl.textContent = users.length;

    let html = `<table class="glass-table">
      <thead>
        <tr>
          <th>ID</th><th>Nombre</th><th>Email</th><th>Rol</th><th>RFC</th><th>Estado</th>
        </tr>
      </thead><tbody>`;

    users.forEach(u => {
      const isAct = u.status === "ACTIVO";
      html += `<tr>
        <td>#${u.id}</td>
        <td><strong>${escapeHtml(u.name)}</strong></td>
        <td>${escapeHtml(u.email)}</td>
        <td><span class="pill-role">${escapeHtml(u.role)}</span></td>
        <td><code class="font-mono text-emerald">${escapeHtml(u.rfc || "-")}</code></td>
        <td><span class="status-pill ${isAct ? 'status-active' : 'status-pending'}">${escapeHtml(u.status)}</span></td>
      </tr>`;
    });

    html += "</tbody></table>";
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = '<p class="text-center p-3 text-muted">Error al cargar usuarios.</p>';
  }
}

async function loadAdminDonations() {
  const container = document.getElementById("admin-donations-container");
  const countEl = document.getElementById("admin-donations-count");
  if (!container) return;

  try {
    const res = await apiFetch("/api/donations");
    if (!res.ok) return;
    const list = await res.json();
    if (countEl) countEl.textContent = list.length;

    if (list.length === 0) {
      container.innerHTML = '<p class="text-center p-3 text-muted">No hay donaciones registradas aún.</p>';
      return;
    }

    let html = `<table class="glass-table">
      <thead>
        <tr><th>Folio</th><th>Donante</th><th>Causa</th><th>Monto</th><th>RFC</th><th>Fecha</th></tr>
      </thead><tbody>`;

    list.forEach(d => {
      html += `<tr>
        <td>#${d.id}</td>
        <td>${escapeHtml(d.donor_name)}</td>
        <td>${escapeHtml(d.cause)}</td>
        <td class="text-emerald font-mono">$${Number(d.amount).toFixed(2)}</td>
        <td><code class="font-mono text-emerald">${escapeHtml(d.rfc || "-")}</code></td>
        <td>${escapeHtml(d.created_at || "-")}</td>
      </tr>`;
    });

    html += "</tbody></table>";
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = '<p class="text-center p-3 text-muted">Error al cargar donaciones.</p>';
  }
}

async function loadAdminAudit() {
  const container = document.getElementById("admin-audit-container");
  if (!container) return;

  try {
    const res = await apiFetch("/api/admin/audit");
    if (!res.ok) return;
    const logs = await res.json();

    if (logs.length === 0) {
      container.innerHTML = '<p class="text-center p-3 text-muted">No hay registros de auditoría.</p>';
      return;
    }

    let html = `<table class="glass-table">
      <thead>
        <tr><th>ID</th><th>Email</th><th>Acción</th><th>Detalles</th><th>IP</th><th>Fecha</th></tr>
      </thead><tbody>`;

    logs.forEach(l => {
      html += `<tr>
        <td>#${l.id}</td>
        <td>${escapeHtml(l.email || "-")}</td>
        <td><span class="pill-role">${escapeHtml(l.accion)}</span></td>
        <td>${escapeHtml(l.detalles || "-")}</td>
        <td><code class="font-mono">${escapeHtml(l.ipAddress || "-")}</code></td>
        <td>${escapeHtml(l.fecha || "-")}</td>
      </tr>`;
    });

    html += "</tbody></table>";
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = '<p class="text-center p-3 text-muted">Error al cargar bitácora de auditoría.</p>';
  }
}

async function approveUser(id) {
  try {
    const res = await apiFetch(`/api/admin/approve?id=${id}`, { method: "POST" });
    const data = await res.json();
    if (res.ok) {
      showToast("Cuenta aprobada y activada exitosamente.", "success");
      loadAdminPending();
    } else {
      showToast(data.detail || "Error al aprobar cuenta.", "error");
    }
  } catch (err) {
    showToast("Error de conexión al aprobar.", "error");
  }
}

async function rejectUser(id) {
  try {
    const res = await apiFetch(`/api/admin/reject?id=${id}`, { method: "POST" });
    const data = await res.json();
    if (res.ok) {
      showToast("Solicitud rechazada.", "info");
      loadAdminPending();
    } else {
      showToast(data.detail || "Error al rechazar cuenta.", "error");
    }
  } catch (err) {
    showToast("Error de conexión al rechazar.", "error");
  }
}

function showToast(message, type = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
