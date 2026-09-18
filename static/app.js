const STORAGE_TOKEN_KEY = "solidaria_jwt_token";
const STORAGE_USER_KEY = "solidaria_jwt_user";

let currentToken = localStorage.getItem(STORAGE_TOKEN_KEY) || null;
let currentUser = null;

try {
  const storedUser = localStorage.getItem(STORAGE_USER_KEY);
  if (storedUser) currentUser = JSON.parse(storedUser);
} catch (e) {
  currentUser = null;
}

document.addEventListener("DOMContentLoaded", () => {
  initAppState();
  initDonationControls();

  document.addEventListener("click", (e) => {
    const widget = document.getElementById("user-session-widget");
    const dropdown = document.getElementById("user-dropdown");
    if (widget && !widget.contains(e.target) && dropdown && !dropdown.classList.contains("hidden")) {
      dropdown.classList.add("hidden");
    }
  });
});

function initAppState() {
  if (currentToken && currentUser && currentUser.status === "ACTIVO") {
    showAppView();
    validateSession();
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
  fetchStats();
}

function updateSessionIcon() {
  if (!currentUser) return;

  const initials = getInitials(currentUser.name);
  const initialsEl = document.getElementById("user-initials");
  const displayNameEl = document.getElementById("user-display-name");
  const displayRoleEl = document.getElementById("user-display-role");
  const fullNameEl = document.getElementById("dropdown-full-name");
  const emailEl = document.getElementById("dropdown-email");
  const rolePill = document.getElementById("dropdown-role-pill");
  const statusPill = document.getElementById("dropdown-status-pill");
  const rfcEl = document.getElementById("dropdown-rfc");

  const bannerNameEl = document.getElementById("banner-user-name");
  const bannerRfcEl = document.getElementById("banner-user-rfc");
  const bannerEntityEl = document.getElementById("banner-entity-info");

  const btnAdminPanel = document.getElementById("btn-admin-panel");
  const btnNavAdmin = document.getElementById("btn-nav-admin");

  if (initialsEl) initialsEl.textContent = initials;
  if (fullNameEl) fullNameEl.textContent = currentUser.name;
  const isAdmin = currentUser.role === "ADMIN";

  if (initialsEl) initialsEl.textContent = isAdmin ? "AD" : "DR";
  if (fullNameEl) fullNameEl.textContent = isAdmin ? currentUser.name : "Donante registrado";
  if (emailEl) emailEl.textContent = currentUser.email;

  const isAdmin = currentUser.role === "ADMIN";

  if (displayNameEl) displayNameEl.textContent = isAdmin ? "👑 Admin" : "Donante registrado";
  if (displayRoleEl) displayRoleEl.textContent = currentUser.role;

  if (rolePill) {
    rolePill.textContent = currentUser.role;
    rolePill.className = isAdmin ? "pill-role admin-pill" : "pill-role";
  }

  if (statusPill) {
    statusPill.textContent = currentUser.status || "ACTIVO";
    statusPill.className = currentUser.status === "ACTIVO" ? "status-pill status-active" : "status-pill status-pending";
  }

  const rfcVal = currentUser.rfc || "Sin RFC";
  if (rfcEl) rfcEl.textContent = rfcVal;

  if (bannerNameEl) bannerNameEl.textContent = isAdmin ? "👑 Superusuario" : "Donante registrado";
  if (bannerRfcEl) bannerRfcEl.textContent = `RFC: ${rfcVal}`;
  if (bannerEntityEl) {
    bannerEntityEl.textContent = currentUser.entity_type || (isAdmin ? "ADMIN" : "ENTIDAD");
  }

  const donorRfcInput = document.getElementById("donor-rfc");
  if (donorRfcInput && currentUser.rfc && !donorRfcInput.value) {
    donorRfcInput.value = currentUser.rfc;
  }

  if (btnAdminPanel) {
    if (isAdmin) btnAdminPanel.classList.remove("hidden");
    else btnAdminPanel.classList.add("hidden");
  }
  if (btnNavAdmin) {
    if (isAdmin) btnNavAdmin.classList.remove("hidden");
    else btnNavAdmin.classList.add("hidden");
  }
}

function toggleUserDropdown() {
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.toggle("hidden");
}

function getInitials(name) {
  if (!name) return "U";
  const parts = name.trim().split(" ");
  if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function scrollToSection(id) {
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: "smooth" });
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
    showToast(currentUser.role === "ADMIN" ? `Bienvenido, ${currentUser.name}` : "Donante registrado", "success");
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

  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

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

function initDonationControls() {
  const causeCards = document.querySelectorAll(".cause-card");
  const selectedCauseInput = document.getElementById("selected-cause-input");

  causeCards.forEach((card) => {
    card.addEventListener("click", () => {
      causeCards.forEach((c) => c.classList.remove("active"));
      card.classList.add("active");
      const causeName = card.getAttribute("data-cause");
      if (selectedCauseInput) selectedCauseInput.value = causeName;
    });
  });

  const amountPills = document.querySelectorAll(".amount-pill");
  const customAmountInput = document.getElementById("custom-amount-input");
  const btnDonationAmountText = document.getElementById("btn-donation-amount-text");

  amountPills.forEach((btn) => {
    btn.addEventListener("click", () => {
      amountPills.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const val = btn.getAttribute("data-val");
      if (customAmountInput) customAmountInput.value = val;
      if (btnDonationAmountText) btnDonationAmountText.textContent = `$${parseFloat(val).toFixed(2)} USD`;
    });
  });

  if (customAmountInput) {
    customAmountInput.addEventListener("input", (e) => {
      const val = parseFloat(e.target.value) || 0;
      amountPills.forEach((b) => {
        if (b.getAttribute("data-val") === e.target.value) {
          b.classList.add("active");
        } else {
          b.classList.remove("active");
        }
      });
      if (btnDonationAmountText) btnDonationAmountText.textContent = `$${val.toFixed(2)} USD`;
    });
  }

  const paymentCards = document.querySelectorAll(".payment-card");
  paymentCards.forEach((card) => {
    card.addEventListener("click", () => {
      paymentCards.forEach((c) => c.classList.remove("active"));
      card.classList.add("active");
      const radio = card.querySelector("input[type='radio']");
      if (radio) radio.checked = true;

      const cardFields = document.getElementById("card-fields");
      if (cardFields) {
        if (radio && radio.value === "tarjeta") {
          cardFields.classList.remove("hidden");
        } else {
          cardFields.classList.add("hidden");
        }
      }
    });
  });
}

function toggleRfcField() {
  const check = document.getElementById("tax-deductible-check");
  const rfcGroup = document.getElementById("rfc-field-group");
  const rfcInput = document.getElementById("donor-rfc");
  if (!check || !rfcGroup) return;

  if (check.checked) {
    rfcGroup.classList.remove("hidden");
    if (rfcInput) {
      if (!rfcInput.value && currentUser && currentUser.rfc) {
        rfcInput.value = currentUser.rfc;
      }
      rfcInput.focus();
    }
  } else {
    rfcGroup.classList.add("hidden");
  }
}

async function handleDonationSubmit(event) {
  event.preventDefault();

  const causeInput = document.getElementById("selected-cause-input");
  const cause = causeInput ? causeInput.value : "Educación para Niños";
  const amountInput = document.getElementById("custom-amount-input").value;
  const amount = parseFloat(amountInput);
  const messageInput = document.getElementById("donation-message");
  const message = messageInput ? messageInput.value.trim() : "";

  const selectedPayment = document.querySelector("input[name='payment_method']:checked");
  const paymentMethod = selectedPayment ? selectedPayment.value : "tarjeta";

  const taxCheck = document.getElementById("tax-deductible-check");
  let rfc = null;
  if (taxCheck && taxCheck.checked) {
    const rfcInput = document.getElementById("donor-rfc");
    const rfcVal = rfcInput ? rfcInput.value.trim().toUpperCase() : "";
    if (rfcVal) rfc = rfcVal;
  }

  if (!amount || amount <= 0) {
    showToast("Ingresa un monto válido mayor a 0.", "error");
    return;
  }

  const payload = {
    amount,
    cause,
    payment_method: paymentMethod,
    message: message || null,
    rfc: rfc || null,
  };

  const submitBtn = document.getElementById("btn-donate-submit");
  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await apiFetch("/api/donations", {
      method: "POST",
      body: JSON.stringify(payload),
    });

    const data = await res.json();

    if (!res.ok) {
      showToast(data.detail || "Error al registrar donación.", "error");
      return;
    }

    showReceiptModal(data);
    fetchStats();
    showToast("Donación completada exitosamente.", "success");

    if (messageInput) messageInput.value = "";
  } catch (err) {
    showToast("Error de conexión al donar.", "error");
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

function showReceiptModal(donation) {
  const elName = document.getElementById("rec-donor-name");
  const elEmail = document.getElementById("rec-donor-email");
  const elRfc = document.getElementById("rec-rfc");
  const elCause = document.getElementById("rec-cause");
  const elAmount = document.getElementById("rec-amount");
  const elId = document.getElementById("rec-id");

  if (elName) elName.textContent = donation.donor_name;
  if (elName) elName.textContent = currentUser && currentUser.role === "ADMIN" ? donation.donor_name : "Donante registrado";
  if (elEmail) elEmail.textContent = donation.donor_email;
  if (elRfc) elRfc.textContent = donation.rfc && donation.rfc.trim() !== "" ? donation.rfc : "No solicitado";
  if (elCause) elCause.textContent = donation.cause;
  if (elAmount) elAmount.textContent = `$${Number(donation.amount).toFixed(2)} USD`;
  if (elId) elId.textContent = `#DON-${donation.id.toString().padStart(5, "0")}`;

  const modal = document.getElementById("receipt-modal");
  if (modal) modal.classList.remove("hidden");
}

function closeReceiptModal() {
  const modal = document.getElementById("receipt-modal");
  if (modal) modal.classList.add("hidden");
}

async function fetchStats() {
  try {
    const res = await fetch("/api/donations/stats");
    if (!res.ok) return;
    const stats = await res.json();

    const totalEl = document.getElementById("stat-total");
    const countEl = document.getElementById("stat-count");
    const fillEl = document.getElementById("progress-bar-fill");

    if (totalEl) totalEl.textContent = `$${Number(stats.total_raised).toLocaleString("en-US", { minimumFractionDigits: 2 })}`;
    if (countEl) countEl.textContent = stats.donations_count;
    if (fillEl) fillEl.style.width = `${stats.progress_percentage}%`;
  } catch (e) {
    console.warn("Error al cargar estadísticas:", e);
  }
}

async function openMyDonationsModal() {
  const modal = document.getElementById("my-donations-modal");
  const container = document.getElementById("donations-list-container");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#94a3b8;'>Cargando historial...</p>";
  modal.classList.remove("hidden");

  try {
    const res = await apiFetch("/api/donations/my-donations");
    if (!res.ok) {
      container.innerHTML = "<p class='glass-alert-error'>No se pudo cargar el historial.</p>";
      return;
    }

    const donations = await res.json();

    if (donations.length === 0) {
      container.innerHTML = "<p style='padding:2rem; text-align:center; color:#94a3b8;'>No tienes donaciones registradas.</p>";
      return;
    }

    let html = `
      <table class="glass-table">
        <thead>
          <tr>
            <th>Folio</th>
            <th>Causa</th>
            <th>Monto</th>
            <th>RFC</th>
            <th>Método</th>
            <th>Fecha</th>
          </tr>
        </thead>
        <tbody>
    `;

    donations.forEach((d) => {
      const rfcDisplay = d.rfc && d.rfc.trim() !== "" ? `<code class="badge-mono text-neon">${escapeHtml(d.rfc)}</code>` : `<span style="color:#64748b;">-</span>`;
      html += `
        <tr>
          <td><code class="badge-mono">#DON-${d.id.toString().padStart(4, "0")}</code></td>
          <td><strong>${escapeHtml(d.cause)}</strong></td>
          <td class="text-emerald-lg font-mono">$${Number(d.amount).toFixed(2)}</td>
          <td>${rfcDisplay}</td>
          <td>${escapeHtml(d.payment_method)}</td>
          <td><small>${escapeHtml(d.created_at)}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = "<p class='glass-alert-error'>Error al consultar donaciones.</p>";
  }
}

function closeMyDonationsModal() {
  const modal = document.getElementById("my-donations-modal");
  if (modal) modal.classList.add("hidden");
}

function openAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  if (!currentUser || currentUser.role !== "ADMIN") {
    showToast("Acceso restringido a administradores.", "error");
    return;
  }

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
    if (btn) {
      if (t === tab) btn.classList.add("active");
      else btn.classList.remove("active");
    }
    if (panel) {
      if (t === tab) panel.classList.remove("hidden");
      else panel.classList.add("hidden");
    }
  });

  if (tab === "pending") loadAdminPendingUsers();
  else if (tab === "users") loadAdminUsers();
  else if (tab === "donations") loadAdminDonations();
  else if (tab === "audit") loadAdminAuditLogs();
}

async function loadAdminPendingUsers() {
  const container = document.getElementById("admin-pending-container");
  const countBadge = document.getElementById("admin-pending-count");
  if (!container) return;

  container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#94a3b8;'>Cargando solicitudes...</p>";

  try {
    const res = await apiFetch("/api/admin/pending-users");
    if (!res.ok) {
      container.innerHTML = "<p class='glass-alert-error'>Error al consultar solicitudes.</p>";
      return;
    }

    const pending = await res.json();
    if (countBadge) countBadge.textContent = pending.length;

    if (pending.length === 0) {
      container.innerHTML = "<p style='padding:2rem; text-align:center; color:#94a3b8;'>No hay solicitudes pendientes.</p>";
      return;
    }

    let html = `
      <table class="glass-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>RFC</th>
            <th>Entidad</th>
            <th>Tipo</th>
            <th>Representante</th>
            <th>Correo</th>
            <th>Rol</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
    `;

    pending.forEach(u => {
      html += `
        <tr>
          <td><code class="badge-mono">#${u.id}</code></td>
          <td><code class="badge-mono text-neon font-bold">${escapeHtml(u.rfc || 'SIN RFC')}</code></td>
          <td><strong>${escapeHtml(u.legal_name || u.name)}</strong></td>
          <td><span class="status-pill status-pending">${escapeHtml(u.entity_type || 'ORGANIZACION_SOCIAL')}</span></td>
          <td>${escapeHtml(u.name)}</td>
          <td><small>${escapeHtml(u.email)}</small></td>
          <td><span class="pill-role">${escapeHtml(u.role)}</span></td>
          <td>
            <button class="btn-action-sm btn-approve" onclick="changeUserStatus(${u.id}, 'ACTIVO')">
              Aprobar
            </button>
            <button class="btn-action-sm btn-reject" onclick="changeUserStatus(${u.id}, 'RECHAZADO')">
              Rechazar
            </button>
          </td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (e) {
    container.innerHTML = "<p class='glass-alert-error'>Error al consultar solicitudes.</p>";
  }
}

async function changeUserStatus(userId, newStatus) {
  try {
    const res = await apiFetch("/api/admin/users/status", {
      method: "POST",
      body: JSON.stringify({ user_id: userId, status: newStatus }),
    });

    const data = await res.json();
    if (!res.ok) {
      showToast(data.detail || "Error al actualizar estado.", "error");
      return;
    }

    showToast(`Cuenta #${userId} actualizada a '${newStatus}'.`, "success");
    loadAdminPendingUsers();
  } catch (err) {
    showToast("Error de conexión al actualizar estado.", "error");
  }
}

async function loadAdminUsers() {
  const container = document.getElementById("admin-users-container");
  const countBadge = document.getElementById("admin-users-count");
  if (!container) return;

  container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#94a3b8;'>Cargando usuarios...</p>";

  try {
    const res = await apiFetch("/api/admin/users");
    if (!res.ok) {
      container.innerHTML = "<p class='glass-alert-error'>Error al consultar usuarios.</p>";
      return;
    }

    const users = await res.json();
    if (countBadge) countBadge.textContent = users.length;

    let html = `
      <table class="glass-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>RFC</th>
            <th>Entidad</th>
            <th>Representante</th>
            <th>Correo</th>
            <th>Rol</th>
            <th>Estado</th>
            <th>Registro</th>
          </tr>
        </thead>
        <tbody>
    `;

    users.forEach(u => {
      const statusClass = u.status === "ACTIVO" ? "status-active" : u.status === "RECHAZADO" ? "status-rejected" : "status-pending";
      html += `
        <tr>
          <td><code class="badge-mono">#${u.id}</code></td>
          <td><code class="badge-mono text-neon">${escapeHtml(u.rfc || '-')}</code></td>
          <td><strong>${escapeHtml(u.legal_name || u.name)}</strong></td>
          <td>${escapeHtml(u.name)}</td>
          <td><small>${escapeHtml(u.email)}</small></td>
          <td><span class="pill-role">${escapeHtml(u.role)}</span></td>
          <td><span class="status-pill ${statusClass}">${escapeHtml(u.status || 'PENDIENTE')}</span></td>
          <td><small>${escapeHtml(u.created_at)}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (e) {
    container.innerHTML = "<p class='glass-alert-error'>Error al cargar usuarios.</p>";
  }
}

async function loadAdminDonations() {
  const container = document.getElementById("admin-donations-container");
  const countBadge = document.getElementById("admin-donations-count");
  if (!container) return;

  container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#94a3b8;'>Cargando donaciones...</p>";

  try {
    const res = await apiFetch("/api/admin/donations");
    if (!res.ok) {
      container.innerHTML = "<p class='glass-alert-error'>Error al consultar donaciones.</p>";
      return;
    }

    const donations = await res.json();
    if (countBadge) countBadge.textContent = donations.length;

    let html = `
      <table class="glass-table">
        <thead>
          <tr>
            <th>Folio</th>
            <th>Donante</th>
            <th>Correo</th>
            <th>RFC</th>
            <th>Causa</th>
            <th>Monto</th>
            <th>Método</th>
            <th>Fecha</th>
          </tr>
        </thead>
        <tbody>
    `;

    donations.forEach(d => {
      const rfcDisplay = d.rfc && d.rfc.trim() !== "" ? `<code class="badge-mono text-neon">${escapeHtml(d.rfc)}</code>` : `<span style="color:#64748b;">-</span>`;
      html += `
        <tr>
          <td><code class="badge-mono">#DON-${String(d.id).padStart(4, '0')}</code></td>
          <td><strong>${escapeHtml(d.donor_name)}</strong></td>
          <td><small>${escapeHtml(d.donor_email)}</small></td>
          <td>${rfcDisplay}</td>
          <td>${escapeHtml(d.cause)}</td>
          <td class="text-emerald-lg font-mono">$${Number(d.amount).toFixed(2)}</td>
          <td>${escapeHtml(d.payment_method)}</td>
          <td><small>${escapeHtml(d.created_at)}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (e) {
    container.innerHTML = "<p class='glass-alert-error'>Error al cargar donaciones.</p>";
  }
}

async function loadAdminAuditLogs() {
  const container = document.getElementById("admin-audit-container");
  if (!container) return;

  container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#94a3b8;'>Cargando bitácora de auditoría...</p>";

  try {
    const res = await apiFetch("/api/admin/audit-logs");
    if (!res.ok) {
      container.innerHTML = "<p class='glass-alert-error'>Error al consultar auditoría.</p>";
      return;
    }

    const logs = await res.json();

    if (logs.length === 0) {
      container.innerHTML = "<p style='padding:2rem; text-align:center; color:#94a3b8;'>No hay registros de auditoría.</p>";
      return;
    }

    let html = `
      <table class="glass-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Fecha</th>
            <th>IP</th>
            <th>Acción</th>
            <th>Usuario</th>
            <th>Detalles</th>
          </tr>
        </thead>
        <tbody>
    `;

    logs.forEach(l => {
      let badgeClass = "badge-mono";
      if (l.accion.includes("FALLIDO") || l.accion.includes("BLOQUEO") || l.accion.includes("RECHAZADO")) {
        badgeClass = "status-pill status-rejected";
      } else if (l.accion.includes("EXITOSO") || l.accion.includes("APROBADO") || l.accion.includes("ACTIVO")) {
        badgeClass = "status-pill status-active";
      } else if (l.accion.includes("REGISTRO") || l.accion.includes("CAMBIO")) {
        badgeClass = "status-pill status-pending";
      }

      html += `
        <tr>
          <td><code class="badge-mono">#${l.id}</code></td>
          <td><small class="font-mono">${escapeHtml(l.fecha)}</small></td>
          <td><code class="badge-mono">${escapeHtml(l.ip_address || '127.0.0.1')}</code></td>
          <td><span class="${badgeClass}">${escapeHtml(l.accion)}</span></td>
          <td><strong>${escapeHtml(l.email || (l.user_id ? '#' + l.user_id : '-'))}</strong></td>
          <td><small>${escapeHtml(l.detalles || '')}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (e) {
    container.innerHTML = "<p class='glass-alert-error'>Error al consultar auditoría.</p>";
  }
}

function showToast(message, type = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const toast = document.createElement("div");
  toast.className = `toast ${type === "success" ? "toast-success" : type === "error" ? "toast-error" : ""}`;
  toast.innerHTML = `<span>${escapeHtml(message)}</span>`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transform = "translateY(10px)";
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
