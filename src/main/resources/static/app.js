/**
 * app.js - Lógica de cliente: Pantalla de Login, Autenticación JWT,
 * Ícono de Sesión, Ventana de Donaciones con RFC y Panel de Superusuario.
 */

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

// Inicialización de la aplicación al cargar el DOM
document.addEventListener("DOMContentLoaded", () => {
  initAppState();
  initDonationControls();

  // Cerrar menú dropdown al hacer click fuera del widget de sesión
  document.addEventListener("click", (e) => {
    const widget = document.getElementById("user-session-widget");
    const dropdown = document.getElementById("user-dropdown");
    if (widget && !widget.contains(e.target) && dropdown && !dropdown.classList.contains("hidden")) {
      dropdown.classList.add("hidden");
    }
  });
});

/* ==========================================================================
   1. CONTROL DE VISTAS (LOGIN VS VENTANA DE DONACIONES)
   ========================================================================== */

function initAppState() {
  if (currentToken && currentUser) {
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
  const fullNameEl = document.getElementById("dropdown-full-name");
  const emailEl = document.getElementById("dropdown-email");
  const bannerNameEl = document.getElementById("banner-user-name");
  const bannerEmailEl = document.getElementById("banner-user-email");
  const jwtBadge = document.querySelector(".jwt-badge");
  const rolePill = document.querySelector(".role-pill");
  const btnAdminPanel = document.getElementById("btn-admin-panel");
  const btnNavAdmin = document.getElementById("btn-nav-admin");

  if (initialsEl) initialsEl.textContent = initials;
  if (fullNameEl) fullNameEl.textContent = currentUser.name;
  if (emailEl) emailEl.textContent = currentUser.email;

  const isAdmin = currentUser.role === "admin";

  if (displayNameEl) {
    displayNameEl.textContent = (isAdmin ? "👑 " : "") + currentUser.name.split(" ")[0];
  }

  if (jwtBadge) {
    if (isAdmin) {
      jwtBadge.textContent = "👑 Superusuario";
      jwtBadge.classList.add("admin-badge");
    } else {
      jwtBadge.textContent = "● JWT Activo";
      jwtBadge.classList.remove("admin-badge");
    }
  }

  if (rolePill) {
    if (isAdmin) {
      rolePill.textContent = "👑 Superusuario (Acceso Total)";
      rolePill.classList.add("admin-pill");
    } else {
      rolePill.textContent = "Donante Autenticado";
      rolePill.classList.remove("admin-pill");
    }
  }

  if (btnAdminPanel) {
    if (isAdmin) {
      btnAdminPanel.classList.remove("hidden");
    } else {
      btnAdminPanel.classList.add("hidden");
    }
  }

  if (btnNavAdmin) {
    if (isAdmin) {
      btnNavAdmin.classList.remove("hidden");
    } else {
      btnNavAdmin.classList.add("hidden");
    }
  }

  if (bannerNameEl) {
    bannerNameEl.textContent = isAdmin
      ? `👑 Superusuario: ${currentUser.name}`
      : currentUser.name;
  }
  if (bannerEmailEl) {
    bannerEmailEl.textContent = isAdmin
      ? `Acceso total habilitado a SQLite (${currentUser.email})`
      : `Vincular aporte a: ${currentUser.email}`;
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

/* ==========================================================================
   2. ACCIONES DE LOGIN / REGISTRO
   ========================================================================== */

function switchLoginTab(tab) {
  const btnLogin = document.getElementById("tab-login-btn");
  const btnRegister = document.getElementById("tab-register-btn");
  const formLogin = document.getElementById("form-login");
  const formRegister = document.getElementById("form-register");
  const errorMsg = document.getElementById("auth-error-msg");

  if (errorMsg) errorMsg.classList.add("hidden");

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

function fillAdminCredentials() {
  document.getElementById("login-email").value = "admin@donaciones.org";
  document.getElementById("login-password").value = "admin1234";
  showToast("Credenciales de Superusuario listas.", "info");
}

function fillDemoCredentials() {
  document.getElementById("login-email").value = "demo@donaciones.org";
  document.getElementById("login-password").value = "demo1234";
  showToast("Credenciales de Donante listas.", "info");
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
    showToast(`¡Bienvenido, ${currentUser.name}!`, "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error al conectar con el servidor.";
      errorMsg.classList.remove("hidden");
    }
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

async function handleRegisterSubmit(event) {
  event.preventDefault();
  const name = document.getElementById("reg-name").value.trim();
  const email = document.getElementById("reg-email").value.trim();
  const password = document.getElementById("reg-password").value;
  const errorMsg = document.getElementById("auth-error-msg");
  const submitBtn = document.getElementById("btn-submit-register");

  if (errorMsg) errorMsg.classList.add("hidden");
  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, email, password }),
    });

    const data = await res.json();

    if (!res.ok) {
      errorMsg.textContent = data.detail || "Error en el registro.";
      errorMsg.classList.remove("hidden");
      return;
    }

    currentToken = data.access_token;
    currentUser = data.user;
    localStorage.setItem(STORAGE_TOKEN_KEY, currentToken);
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));

    showAppView();
    showToast(`¡Cuenta creada con éxito para ${currentUser.name}!`, "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error al conectar con el servidor.";
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
    showToast("Sesión cerrada exitosamente.", "info");
  }
}

/* ==========================================================================
   3. CLIENTE HTTP CON CABECERA BEARER JWT
   ========================================================================== */

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
    showToast("Tu sesión JWT ha expirado. Ingresa de nuevo.", "error");
    logout(false);
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
    console.warn("No se pudo validar sesión:", err);
  }
}

/* ==========================================================================
   4. CONTROL DE DONACIONES Y RFC FISCAL
   ========================================================================== */

function initDonationControls() {
  // Selección de Causas (pills simplificadas)
  const causePills = document.querySelectorAll(".cause-pill, .cause-option");
  const selectedCauseInput = document.getElementById("selected-cause-input");

  causePills.forEach((pill) => {
    pill.addEventListener("click", () => {
      causePills.forEach((p) => p.classList.remove("active"));
      pill.classList.add("active");
      const causeName = pill.getAttribute("data-cause");
      if (selectedCauseInput) selectedCauseInput.value = causeName;
    });
  });

  // Botones de montos predefinidos
  const amountButtons = document.querySelectorAll(".amount-btn");
  const customAmountInput = document.getElementById("custom-amount-input");
  const btnDonationAmountText = document.getElementById("btn-donation-amount-text");

  amountButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      amountButtons.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const val = btn.getAttribute("data-val");
      if (customAmountInput) customAmountInput.value = val;
      if (btnDonationAmountText) btnDonationAmountText.textContent = `$${parseFloat(val).toFixed(2)} USD`;
    });
  });

  // Input de monto libre
  if (customAmountInput) {
    customAmountInput.addEventListener("input", (e) => {
      const val = parseFloat(e.target.value) || 0;
      amountButtons.forEach((b) => {
        if (b.getAttribute("data-val") === e.target.value) {
          b.classList.add("active");
        } else {
          b.classList.remove("active");
        }
      });
      if (btnDonationAmountText) btnDonationAmountText.textContent = `$${val.toFixed(2)} USD`;
    });
  }

  // Métodos de pago
  const paymentCards = document.querySelectorAll(".payment-method-card");
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

// Toggle para mostrar/ocultar el campo RFC
function toggleRfcField() {
  const check = document.getElementById("tax-deductible-check");
  const rfcGroup = document.getElementById("rfc-field-group");
  const rfcInput = document.getElementById("donor-rfc");
  if (!check || !rfcGroup) return;

  if (check.checked) {
    rfcGroup.classList.remove("hidden");
    if (rfcInput) rfcInput.focus();
  } else {
    rfcGroup.classList.add("hidden");
    if (rfcInput) rfcInput.value = "";
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

  // Extraer RFC si el checkbox está activo
  const taxCheck = document.getElementById("tax-deductible-check");
  let rfc = null;
  if (taxCheck && taxCheck.checked) {
    const rfcInput = document.getElementById("donor-rfc");
    const rfcVal = rfcInput ? rfcInput.value.trim().toUpperCase() : "";
    if (rfcVal) {
      rfc = rfcVal;
    }
  }

  if (!amount || amount <= 0) {
    showToast("Por favor ingresa un monto válido mayor a $0.", "error");
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
      showToast(data.detail || "Error al procesar la donación.", "error");
      return;
    }

    // Mostrar recibo y actualizar estadísticas
    showReceiptModal(data);
    fetchStats();
    showToast("¡Donación procesada exitosamente!", "success");

    // Limpiar campos opcionales
    if (messageInput) messageInput.value = "";
    if (taxCheck) taxCheck.checked = false;
    toggleRfcField();
  } catch (err) {
    showToast("Error de conexión al registrar la donación.", "error");
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

/* ==========================================================================
   5. RECIBO Y ESTADÍSTICAS
   ========================================================================== */

function showReceiptModal(donation) {
  const elName = document.getElementById("rec-donor-name");
  const elEmail = document.getElementById("rec-donor-email");
  const elRfc = document.getElementById("rec-rfc");
  const elCause = document.getElementById("rec-cause");
  const elAmount = document.getElementById("rec-amount");
  const elPayment = document.getElementById("rec-payment");
  const elId = document.getElementById("rec-id");

  if (elName) elName.textContent = donation.donor_name;
  if (elEmail) elEmail.textContent = donation.donor_email;
  if (elRfc) elRfc.textContent = donation.rfc && donation.rfc.trim() !== "" ? donation.rfc : "No solicitado";
  if (elCause) elCause.textContent = donation.cause;
  if (elAmount) elAmount.textContent = `$${Number(donation.amount).toFixed(2)} USD`;
  if (elPayment) elPayment.textContent = donation.payment_method.toUpperCase();
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
    console.warn("No se pudieron cargar estadísticas:", e);
  }
}

/* ==========================================================================
   6. HISTORIAL DE DONACIONES (PROTEGIDO CON JWT)
   ========================================================================== */

async function openMyDonationsModal() {
  const modal = document.getElementById("my-donations-modal");
  const container = document.getElementById("donations-list-container");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  container.innerHTML = "<p style='padding:1.5rem; color:#64748b; text-align:center;'>Cargando historial protegido...</p>";
  modal.classList.remove("hidden");

  try {
    const res = await apiFetch("/api/donations/my-donations");
    if (!res.ok) {
      container.innerHTML = "<p class='error-badge'>No se pudo cargar el historial. Sesión no válida.</p>";
      return;
    }

    const donations = await res.json();

    if (donations.length === 0) {
      container.innerHTML = "<p style='padding:2rem; text-align:center; color:#64748b;'>Aún no tienes donaciones registradas con tu cuenta.</p>";
      return;
    }

    let html = `
      <table class="donations-table">
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
      const rfcDisplay = d.rfc && d.rfc.trim() !== "" ? `<span class="badge-rfc">${escapeHtml(d.rfc)}</span>` : `<span style="color:#94a3b8;">-</span>`;
      html += `
        <tr>
          <td><span class="code-tag">#DON-${d.id.toString().padStart(4, "0")}</span></td>
          <td><strong>${escapeHtml(d.cause)}</strong></td>
          <td class="text-green font-bold">$${Number(d.amount).toFixed(2)}</td>
          <td>${rfcDisplay}</td>
          <td>${escapeHtml(d.payment_method)}</td>
          <td><small>${escapeHtml(d.created_at)}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = "<p class='error-badge'>Error al consultar la API de donaciones.</p>";
  }
}

function closeMyDonationsModal() {
  const modal = document.getElementById("my-donations-modal");
  if (modal) modal.classList.add("hidden");
}

/* ==========================================================================
   7. INSPECTOR DE TOKEN JWT (RFC 7519)
   ========================================================================== */

function openJwtInspectorModal() {
  const modal = document.getElementById("jwt-inspector-modal");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  const headerDisplay = document.getElementById("jwt-header-display");
  const payloadDisplay = document.getElementById("jwt-payload-display");

  if (!currentToken) {
    if (headerDisplay) headerDisplay.textContent = "{}";
    if (payloadDisplay) payloadDisplay.textContent = "{}";
  } else {
    const parts = currentToken.split(".");
    if (parts.length === 3) {
      try {
        const header = JSON.parse(atob(parts[0]));
        const payload = JSON.parse(atob(parts[1]));
        if (headerDisplay) headerDisplay.textContent = JSON.stringify(header, null, 2);
        if (payloadDisplay) payloadDisplay.textContent = JSON.stringify(payload, null, 2);
      } catch (e) {
        if (headerDisplay) headerDisplay.textContent = "// Error al decodificar base64";
        if (payloadDisplay) payloadDisplay.textContent = "// Error al decodificar base64";
      }
    }
  }

  if (modal) modal.classList.remove("hidden");
}

function closeJwtInspectorModal() {
  const modal = document.getElementById("jwt-inspector-modal");
  if (modal) modal.classList.add("hidden");
}

/* ==========================================================================
   8. PANEL DE SUPERUSUARIO (BASE DE DATOS SQLITE)
   ========================================================================== */

function openAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  if (!currentUser || currentUser.role !== "admin") {
    showToast("Se requieren privilegios de Superusuario.", "error");
    return;
  }

  if (modal) modal.classList.remove("hidden");
  loadAdminData();
}

function closeAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  if (modal) modal.classList.add("hidden");
}

function switchAdminTab(tab) {
  const tabUsersBtn = document.getElementById("tab-admin-users-btn");
  const tabDonationsBtn = document.getElementById("tab-admin-donations-btn");
  const usersContainer = document.getElementById("admin-users-container");
  const donationsContainer = document.getElementById("admin-donations-container");

  if (tab === "users") {
    if (tabUsersBtn) tabUsersBtn.classList.add("active");
    if (tabDonationsBtn) tabDonationsBtn.classList.remove("active");
    if (usersContainer) usersContainer.classList.remove("hidden");
    if (donationsContainer) donationsContainer.classList.add("hidden");
  } else {
    if (tabDonationsBtn) tabDonationsBtn.classList.add("active");
    if (tabUsersBtn) tabUsersBtn.classList.remove("active");
    if (donationsContainer) donationsContainer.classList.remove("hidden");
    if (usersContainer) usersContainer.classList.add("hidden");
  }
}

async function loadAdminData() {
  const usersContainer = document.getElementById("admin-users-container");
  const donationsContainer = document.getElementById("admin-donations-container");
  const usersCountEl = document.getElementById("admin-users-count");
  const donationsCountEl = document.getElementById("admin-donations-count");

  if (usersContainer) usersContainer.innerHTML = "<p style='padding:1.5rem; color:#64748b; text-align:center;'>Cargando usuarios desde SQLite...</p>";
  if (donationsContainer) donationsContainer.innerHTML = "<p style='padding:1.5rem; color:#64748b; text-align:center;'>Cargando donaciones desde SQLite...</p>";

  // 1. Usuarios
  try {
    const resUsers = await apiFetch("/api/admin/users");
    if (resUsers.ok) {
      const users = await resUsers.json();
      if (usersCountEl) usersCountEl.textContent = users.length;

      let htmlUsers = `
        <table class="donations-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Nombre</th>
              <th>Correo Electrónico</th>
              <th>Rol</th>
              <th>Fecha de Registro</th>
            </tr>
          </thead>
          <tbody>
      `;
      users.forEach(u => {
        const isAdmin = u.role === "admin";
        htmlUsers += `
          <tr>
            <td><strong>#${u.id}</strong></td>
            <td>${escapeHtml(u.name)}</td>
            <td>${escapeHtml(u.email)}</td>
            <td><span class="${isAdmin ? 'role-pill admin-pill' : 'role-pill'}">${isAdmin ? '👑 Superusuario' : '👤 Donante'}</span></td>
            <td><small>${escapeHtml(u.created_at)}</small></td>
          </tr>
        `;
      });
      htmlUsers += `</tbody></table>`;
      if (usersContainer) usersContainer.innerHTML = htmlUsers;
    } else {
      if (usersContainer) usersContainer.innerHTML = "<p class='error-badge'>Error al cargar usuarios de la BD.</p>";
    }
  } catch (e) {
    if (usersContainer) usersContainer.innerHTML = "<p class='error-badge'>Error de conexión con la BD.</p>";
  }

  // 2. Donaciones
  try {
    const resDonations = await apiFetch("/api/admin/donations");
    if (resDonations.ok) {
      const donations = await resDonations.json();
      if (donationsCountEl) donationsCountEl.textContent = donations.length;

      let htmlDons = `
        <table class="donations-table">
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
        const rfcDisplay = d.rfc && d.rfc.trim() !== "" ? `<span class="badge-rfc">${escapeHtml(d.rfc)}</span>` : `<span style="color:#94a3b8;">-</span>`;
        htmlDons += `
          <tr>
            <td><span class="code-tag">#DON-${String(d.id).padStart(4, '0')}</span></td>
            <td><strong>${escapeHtml(d.donor_name)}</strong></td>
            <td><small>${escapeHtml(d.donor_email)}</small></td>
            <td>${rfcDisplay}</td>
            <td>${escapeHtml(d.cause)}</td>
            <td class="text-green font-bold">$${Number(d.amount).toFixed(2)}</td>
            <td>${escapeHtml(d.payment_method)}</td>
            <td><small>${escapeHtml(d.created_at)}</small></td>
          </tr>
        `;
      });
      htmlDons += `</tbody></table>`;
      if (donationsContainer) donationsContainer.innerHTML = htmlDons;
    } else {
      if (donationsContainer) donationsContainer.innerHTML = "<p class='error-badge'>Error al cargar donaciones de la BD.</p>";
    }
  } catch (e) {
    if (donationsContainer) donationsContainer.innerHTML = "<p class='error-badge'>Error de conexión con la BD.</p>";
  }
}

/* ==========================================================================
   9. UTILIDADES
   ========================================================================== */

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
