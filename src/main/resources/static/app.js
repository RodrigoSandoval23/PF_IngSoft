/**
 * app.js - Lógica de cliente: Pantalla de Login, Autenticación JWT,
 * Ícono de Sesión y Ventana de Donaciones.
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

// Inicialización de la aplicación
document.addEventListener("DOMContentLoaded", () => {
  initAppState();
  initDonationControls();

  // Cerrar dropdown al hacer click fuera del widget de sesión
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
  const loginView = document.getElementById("login-view");
  const appView = document.getElementById("app-view");

  if (currentToken && currentUser) {
    // Si hay token guardado, mostramos la ventana de donaciones y validamos el JWT
    showAppView();
    validateSession();
  } else {
    // Si no hay sesión, mostramos la pantalla principal de LOGIN
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
      rolePill.textContent = "👑 Superusuario (Acceso Total a BD)";
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

  if (bannerNameEl) {
    bannerNameEl.textContent = isAdmin
      ? `👑 Sesión de Superusuario: ${currentUser.name}`
      : `Sesión iniciada con JWT: ${currentUser.name}`;
  }
  if (bannerEmailEl) {
    bannerEmailEl.textContent = isAdmin
      ? `Tienes privilegios de administración y acceso directo a la Base de Datos SQLite.`
      : `Tus aportes se vincularán automáticamente a tu cuenta (${currentUser.email}).`;
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

/* ==========================================================================
   2. PESTAÑAS Y ACCIONES DE LOGIN / REGISTRO
   ========================================================================== */

function switchLoginTab(tab) {
  const btnLogin = document.getElementById("tab-login-btn");
  const btnRegister = document.getElementById("tab-register-btn");
  const formLogin = document.getElementById("form-login");
  const formRegister = document.getElementById("form-register");
  const errorMsg = document.getElementById("auth-error-msg");

  if (errorMsg) errorMsg.classList.add("hidden");

  if (tab === "login") {
    btnLogin.classList.add("active");
    btnRegister.classList.remove("active");
    formLogin.classList.remove("hidden");
    formRegister.classList.add("hidden");
  } else {
    btnRegister.classList.add("active");
    btnLogin.classList.remove("active");
    formRegister.classList.remove("hidden");
    formLogin.classList.add("hidden");
  }
}

function fillAdminCredentials() {
  document.getElementById("login-email").value = "admin@donaciones.org";
  document.getElementById("login-password").value = "admin1234";
  showToast("Credenciales de Superusuario cargadas.", "info");
}

function fillDemoCredentials() {
  document.getElementById("login-email").value = "demo@donaciones.org";
  document.getElementById("login-password").value = "demo1234";
  showToast("Credenciales de Donante cargadas.", "info");
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

    // Guardar token JWT y usuario
    currentToken = data.access_token;
    currentUser = data.user;
    localStorage.setItem(STORAGE_TOKEN_KEY, currentToken);
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));

    // Cambiar a la vista de Donaciones
    showAppView();
    showToast(`¡Bienvenido, ${currentUser.name}! Sesión JWT iniciada con éxito.`, "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error de conexión con el servidor Java.";
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

    // Guardar token JWT emitido y usuario
    currentToken = data.access_token;
    currentUser = data.user;
    localStorage.setItem(STORAGE_TOKEN_KEY, currentToken);
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));

    // Desbloquear ventana de donaciones
    showAppView();
    showToast(`¡Cuenta creada! Token JWT emitido para ${currentUser.name}.`, "success");
  } catch (err) {
    if (errorMsg) {
      errorMsg.textContent = "Error de conexión con el servidor Java.";
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
    showToast("Has cerrado sesión. Token JWT destruido.", "info");
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
    showToast("Tu sesión JWT ha expirado. Por favor ingresa nuevamente.", "error");
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
   4. INTERACCIONES DE LA VENTANA DE DONACIONES
   ========================================================================== */

function initDonationControls() {
  // Selección de Causas
  const causeOptions = document.querySelectorAll(".cause-option");
  const selectedCauseInput = document.getElementById("selected-cause-input");

  causeOptions.forEach((option) => {
    option.addEventListener("click", () => {
      causeOptions.forEach((o) => o.classList.remove("active"));
      option.classList.add("active");
      const causeName = option.getAttribute("data-cause");
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
      if (btnDonationAmountText) btnDonationAmountText.textContent = `$${val} USD`;
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

  // Métodos de pago simulados
  const paymentCards = document.querySelectorAll(".payment-method-card");
  paymentCards.forEach((card) => {
    card.addEventListener("click", () => {
      paymentCards.forEach((c) => c.classList.remove("active"));
      card.classList.add("active");
      const radio = card.querySelector("input[type='radio']");
      if (radio) radio.checked = true;

      const cardFields = document.getElementById("card-fields");
      if (cardFields) {
        if (radio.value === "tarjeta") {
          cardFields.classList.remove("hidden");
        } else {
          cardFields.classList.add("hidden");
        }
      }
    });
  });
}

async function handleDonationSubmit(event) {
  event.preventDefault();

  const cause = document.getElementById("selected-cause-input").value;
  const amountInput = document.getElementById("custom-amount-input").value;
  const amount = parseFloat(amountInput);
  const message = document.getElementById("donation-message").value.trim();

  const selectedPayment = document.querySelector("input[name='payment_method']:checked");
  const paymentMethod = selectedPayment ? selectedPayment.value : "tarjeta";

  if (!amount || amount <= 0) {
    showToast("Por favor ingresa un monto válido mayor a $0.", "error");
    return;
  }

  const payload = {
    amount,
    cause,
    payment_method: paymentMethod,
    message: message || null,
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

    // Limpiar mensaje
    document.getElementById("donation-message").value = "";
  } catch (err) {
    showToast("Error de conexión al procesar la donación.", "error");
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

/* ==========================================================================
   5. RECIBO Y ESTADÍSTICAS
   ========================================================================== */

function showReceiptModal(donation) {
  document.getElementById("rec-donor-name").textContent = donation.donor_name;
  document.getElementById("rec-donor-email").textContent = donation.donor_email;
  document.getElementById("rec-cause").textContent = donation.cause;
  document.getElementById("rec-amount").textContent = `$${donation.amount.toFixed(2)} USD`;
  document.getElementById("rec-payment").textContent = donation.payment_method.toUpperCase();
  document.getElementById("rec-id").textContent = `#DON-${donation.id.toString().padStart(5, "0")}`;

  document.getElementById("receipt-modal").classList.remove("hidden");
}

function closeReceiptModal() {
  document.getElementById("receipt-modal").classList.add("hidden");
}

async function fetchStats() {
  try {
    const res = await fetch("/api/donations/stats");
    if (!res.ok) return;
    const stats = await res.json();

    document.getElementById("stat-total").textContent = `$${Number(stats.total_raised).toLocaleString("en-US", { minimumFractionDigits: 2 })}`;
    document.getElementById("stat-count").textContent = stats.donations_count;
    document.getElementById("progress-bar-fill").style.width = `${stats.progress_percentage}%`;
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

  container.innerHTML = "<p style='padding:1rem; color:#64748b;'>Cargando historial protegido con JWT...</p>";
  modal.classList.remove("hidden");

  try {
    const res = await apiFetch("/api/donations/my-donations");
    if (!res.ok) {
      container.innerHTML = "<p class='auth-error'>No se pudo cargar el historial. Sesión no válida.</p>";
      return;
    }

    const donations = await res.json();

    if (donations.length === 0) {
      container.innerHTML = "<p style='padding:1.5rem; text-align:center; color:#64748b;'>Aún no tienes donaciones registradas con tu cuenta.</p>";
      return;
    }

    let html = `
      <table class="donations-table">
        <thead>
          <tr>
            <th>Folio</th>
            <th>Causa</th>
            <th>Monto</th>
            <th>Método</th>
            <th>Fecha</th>
          </tr>
        </thead>
        <tbody>
    `;

    donations.forEach((d) => {
      html += `
        <tr>
          <td><span class="code-tag">#DON-${d.id.toString().padStart(4, "0")}</span></td>
          <td><strong>${escapeHtml(d.cause)}</strong></td>
          <td class="text-green font-bold">$${d.amount.toFixed(2)}</td>
          <td>${escapeHtml(d.payment_method)}</td>
          <td><small>${escapeHtml(d.created_at)}</small></td>
        </tr>
      `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = "<p class='auth-error'>Error al consultar la API de donaciones.</p>";
  }
}

function closeMyDonationsModal() {
  document.getElementById("my-donations-modal").classList.add("hidden");
}

/* ==========================================================================
   7. INSPECTOR DE TOKEN JWT (RFC 7519)
   ========================================================================== */

function openJwtInspectorModal() {
  const modal = document.getElementById("jwt-inspector-modal");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  const rawDisplay = document.getElementById("raw-jwt-display");
  const headerDisplay = document.getElementById("jwt-header-display");
  const payloadDisplay = document.getElementById("jwt-payload-display");

  if (!currentToken) {
    rawDisplay.textContent = "No hay ningún token JWT activo.";
    headerDisplay.textContent = "{}";
    payloadDisplay.textContent = "{}";
  } else {
    rawDisplay.textContent = currentToken;
    const parts = currentToken.split(".");
    if (parts.length === 3) {
      try {
        const header = JSON.parse(atob(parts[0]));
        const payload = JSON.parse(atob(parts[1]));
        headerDisplay.textContent = JSON.stringify(header, null, 2);
        payloadDisplay.textContent = JSON.stringify(payload, null, 2);
      } catch (e) {
        headerDisplay.textContent = "// Error al decodificar base64url";
        payloadDisplay.textContent = "// Error al decodificar base64url";
      }
    }
  }

  modal.classList.remove("hidden");
}

function closeJwtInspectorModal() {
  document.getElementById("jwt-inspector-modal").classList.add("hidden");
}

/* ==========================================================================
   7.5. PANEL DE SUPERUSUARIO (BASE DE DATOS SQLITE)
   ========================================================================== */

function openAdminPanelModal() {
  const modal = document.getElementById("admin-modal");
  const dropdown = document.getElementById("user-dropdown");
  if (dropdown) dropdown.classList.add("hidden");

  if (!currentUser || currentUser.role !== "admin") {
    showToast("Se requieren privilegios de Superusuario.", "error");
    return;
  }

  modal.classList.remove("hidden");
  loadAdminData();
}

function closeAdminPanelModal() {
  document.getElementById("admin-modal").classList.add("hidden");
}

function switchAdminTab(tab) {
  const tabUsersBtn = document.getElementById("tab-admin-users-btn");
  const tabDonationsBtn = document.getElementById("tab-admin-donations-btn");
  const usersContainer = document.getElementById("admin-users-container");
  const donationsContainer = document.getElementById("admin-donations-container");

  if (tab === "users") {
    tabUsersBtn.classList.add("active");
    tabDonationsBtn.classList.remove("active");
    usersContainer.classList.remove("hidden");
    donationsContainer.classList.add("hidden");
  } else {
    tabDonationsBtn.classList.add("active");
    tabUsersBtn.classList.remove("active");
    donationsContainer.classList.remove("hidden");
    usersContainer.classList.add("hidden");
  }
}

async function loadAdminData() {
  const usersContainer = document.getElementById("admin-users-container");
  const donationsContainer = document.getElementById("admin-donations-container");
  const usersCountEl = document.getElementById("admin-users-count");
  const donationsCountEl = document.getElementById("admin-donations-count");

  usersContainer.innerHTML = "<p style='padding:1rem; color:#64748b;'>Cargando usuarios desde SQLite...</p>";
  donationsContainer.innerHTML = "<p style='padding:1rem; color:#64748b;'>Cargando donaciones desde SQLite...</p>";

  // 1. Cargar Usuarios
  try {
    const resUsers = await apiFetch("/api/admin/users");
    if (resUsers.ok) {
      const users = await resUsers.json();
      usersCountEl.textContent = users.length;

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
      usersContainer.innerHTML = htmlUsers;
    } else {
      usersContainer.innerHTML = "<p class='auth-error'>Error al cargar usuarios de la BD.</p>";
    }
  } catch (e) {
    usersContainer.innerHTML = "<p class='auth-error'>Error de conexión con la BD.</p>";
  }

  // 2. Cargar Donaciones
  try {
    const resDonations = await apiFetch("/api/admin/donations");
    if (resDonations.ok) {
      const donations = await resDonations.json();
      donationsCountEl.textContent = donations.length;

      let htmlDons = `
        <table class="donations-table">
          <thead>
            <tr>
              <th>Folio</th>
              <th>Donante</th>
              <th>Correo</th>
              <th>Causa</th>
              <th>Monto</th>
              <th>Método</th>
              <th>Fecha</th>
            </tr>
          </thead>
          <tbody>
      `;
      donations.forEach(d => {
        htmlDons += `
          <tr>
            <td><span class="code-tag">#DON-${String(d.id).padStart(4, '0')}</span></td>
            <td><strong>${escapeHtml(d.donor_name)}</strong></td>
            <td><small>${escapeHtml(d.donor_email)}</small></td>
            <td>${escapeHtml(d.cause)}</td>
            <td class="text-green font-bold">$${Number(d.amount).toFixed(2)}</td>
            <td>${escapeHtml(d.payment_method)}</td>
            <td><small>${escapeHtml(d.created_at)}</small></td>
          </tr>
        `;
      });
      htmlDons += `</tbody></table>`;
      donationsContainer.innerHTML = htmlDons;
    } else {
      donationsContainer.innerHTML = "<p class='auth-error'>Error al cargar donaciones de la BD.</p>";
    }
  } catch (e) {
    donationsContainer.innerHTML = "<p class='auth-error'>Error de conexión con la BD.</p>";
  }
}

/* ==========================================================================
   8. UTILIDADES
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
