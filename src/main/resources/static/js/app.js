// Shared helpers for every screen: API calls, login check, navigation bar, formatting, toasts, modals.
const API = '/api/v1';

/** Call the REST API. Throws an Error with .code / .field (SRS error body) when the call fails. */
async function api(path, options = {}) {
  const opts = { credentials: 'same-origin', headers: {}, ...options };
  if (opts.body && typeof opts.body !== 'string' && !(opts.body instanceof FormData)) {
    opts.body = JSON.stringify(opts.body);
    opts.headers['Content-Type'] = 'application/json';
  }
  let res;
  try {
    res = await fetch(API + path, opts);
  } catch (e) {
    const err = new Error('Cannot reach the server. Check that the app is running.');
    err.code = 'ERR-NET-001';
    throw err;
  }
  if (res.status === 401 && !path.startsWith('/auth/login') && !path.startsWith('/public/')) {
    location.href = 'login.html?next=' + encodeURIComponent(location.pathname.replace(/^\//, '') + location.search);
    return new Promise(() => {});   // page is leaving for the login screen
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error((data && data.message) || ('Request failed (' + res.status + ')'));
    err.code = data && data.code;
    err.field = data && data.field;
    err.status = res.status;
    throw err;
  }
  return data;
}

const inr = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const inr0 = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });
function rs(n) { return '₹' + inr.format(Number(n || 0)); }
function rs0(n) { return '₹' + inr0.format(Number(n || 0)); }
function pct(n) { return Number(n).toString().replace(/\.0+$/, '') + '%'; }

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function fmtDateTime(s) {
  if (!s) return '';
  const d = new Date(s);
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
function fmtDate(s) {
  if (!s) return '';
  return new Date(s).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
function isoDate(d) {
  const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return z.toISOString().slice(0, 10);
}
function param(name) { return new URLSearchParams(location.search).get(name); }
function debounce(fn, ms = 250) {
  let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

/** "ERR-BIL-003 Payments add up to..." as an inline red message (SRS 3.2: error code next to the text). */
function errorHtml(err) {
  return `<div class="msg err">${err.code ? `<span class="code">${esc(err.code)}</span>` : ''}${esc(err.message)}</div>`;
}
function showError(el, err) { el.innerHTML = errorHtml(err); el.classList.remove('hidden'); }
function clearMsg(el) { el.innerHTML = ''; }

function toast(message, type = '') {
  let box = document.getElementById('toasts');
  if (!box) { box = document.createElement('div'); box.id = 'toasts'; document.body.appendChild(box); }
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = message;
  box.appendChild(t);
  setTimeout(() => t.remove(), type === 'err' ? 7000 : 4500);
}

/** Simple modal. Returns {el, close}. */
function openModal(html, { wide = false, onClose } = {}) {
  const back = document.createElement('div');
  back.className = 'modal-back';
  back.innerHTML = `<div class="modal ${wide ? 'wide' : ''}">${html}</div>`;
  document.body.appendChild(back);
  const close = () => { back.remove(); document.removeEventListener('keydown', onKey); onClose && onClose(); };
  const onKey = e => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  back.addEventListener('mousedown', e => { if (e.target === back) close(); });
  back.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', close));
  return { el: back.querySelector('.modal'), close };
}

function confirmBox(message, okLabel = 'Yes') {
  return new Promise(resolve => {
    const m = openModal(`<h2>Please confirm</h2><p>${esc(message)}</p>
      <div class="actions"><button data-close>Cancel</button><button class="primary" id="cfOk">${esc(okLabel)}</button></div>`,
      { onClose: () => resolve(false) });
    m.el.querySelector('#cfOk').onclick = () => { resolve(true); m.close(); };
  });
}

/** Ask for the Owner PIN (discount override, late return). Resolves to the PIN or null. */
function askOwnerPin(reason) {
  return new Promise(resolve => {
    let done = false;
    const m = openModal(`<h2>Owner PIN needed</h2><p class="muted">${esc(reason)}</p>
      <div class="field"><label>Owner PIN</label><input type="password" id="pinIn" inputmode="numeric" maxlength="6" autocomplete="off"></div>
      <div class="actions"><button data-close>Cancel</button><button class="primary" id="pinOk">Approve</button></div>`,
      { onClose: () => { if (!done) resolve(null); } });
    const inp = m.el.querySelector('#pinIn');
    inp.focus();
    const ok = () => { done = true; resolve(inp.value.trim()); m.close(); };
    m.el.querySelector('#pinOk').onclick = ok;
    inp.addEventListener('keydown', e => { if (e.key === 'Enter') ok(); });
  });
}

// ---------- login check + navigation ----------

const NAV = [
  { href: 'dashboard.html', label: 'Dashboard', roles: ['OWNER'] },
  { href: 'pos.html', label: 'POS Billing', roles: ['OWNER', 'SALES_STAFF'] },
  { href: 'products.html', label: 'Products', roles: ['OWNER', 'SALES_STAFF'] },
  { href: 'stock-in.html', label: 'Stock-in', roles: ['OWNER'] },
  { href: 'customers.html', label: 'Customers', roles: ['OWNER', 'SALES_STAFF'] },
  { href: 'returns.html', label: 'Returns', roles: ['OWNER', 'SALES_STAFF'] },
];

function homeFor(me) { return me.role === 'OWNER' ? 'dashboard.html' : 'pos.html'; }

/** Every protected page calls this first. Redirects to login, or home if the role may not see the page. */
async function requireLogin(allowedRoles) {
  const me = await api('/auth/me');
  if (allowedRoles && !allowedRoles.includes(me.role)) {
    location.href = homeFor(me);
    return new Promise(() => {});   // page is leaving; never run the rest of its setup
  }
  renderTopbar(me);
  window.ME = me;
  return me;
}

function renderTopbar(me) {
  const page = location.pathname.split('/').pop() || 'index.html';
  const links = NAV.filter(n => n.roles.includes(me.role))
    .map(n => `<a href="${n.href}" class="${page === n.href || (page === 'product-edit.html' && n.href === 'products.html') ? 'active' : ''}">${n.label}</a>`)
    .join('');
  const bar = document.createElement('header');
  bar.className = 'topbar';
  bar.innerHTML = `<div class="logo">Mansi Collection<small>CSMS</small></div><nav>${links}</nav>
    <div class="user">${esc(me.fullName)}<span class="role">${me.role === 'OWNER' ? 'Owner' : 'Staff'}</span>
    <button class="small" id="logoutBtn">Log out</button></div>`;
  document.body.prepend(bar);
  bar.querySelector('#logoutBtn').onclick = async () => {
    await api('/auth/logout', { method: 'POST' }).catch(() => {});
    location.href = 'login.html';
  };
}

function stockBadge(v) {
  const q = v.stockQty ?? v.stock_qty, r = v.reorderLevel ?? v.reorder_level;
  if (q <= 0) return '<span class="badge err">Out of stock</span>';
  if (q <= r) return `<span class="badge warn">Low: ${q}</span>`;
  return `<span class="badge ok">${q} in stock</span>`;
}
