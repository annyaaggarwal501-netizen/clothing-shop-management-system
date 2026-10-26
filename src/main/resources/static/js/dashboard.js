// Owner dashboard (SRS 3.2): today's KPIs, GST by slab, low stock, alerts, 30-day chart, sales report + Excel.
const $ = id => document.getElementById(id);

requireLogin(['OWNER']).then(() => {
  $('refresh').onclick = load;
  $('preset').onchange = applyPreset;
  $('from').onchange = $('to').onchange = () => { $('preset').value = 'custom'; };
  $('runReport').onclick = runReport;
  $('exportXlsx').onclick = () => {
    location.href = `${API}/reports/sales.xlsx?from=${$('from').value}&to=${$('to').value}`;
  };
  applyPreset();
  load();
  runReport();
});

async function load() {
  const d = await api('/reports/dashboard');
  const t = d.totals;
  $('today').textContent = fmtDate(d.date);
  const gst = Number(t.cgst) + Number(t.sgst);
  $('kpis').innerHTML = [
    ['Sales today', rs0(t.net_sales), `${t.bills} bill${t.bills === 1 ? '' : 's'}`],
    ['Bills', t.bills, `Average ${rs0(t.avg_bill)}`],
    ['GST collected', rs(gst), `CGST ${rs(t.cgst)} · SGST ${rs(t.sgst)}`],
    ['Discounts given', rs0(t.discounts), `on ${rs0(t.gross)} gross`],
    ['Returns', rs0(t.returns_value), `${t.credit_notes} credit notes · net ${rs0(t.net_after_returns)}`],
    ['Stock alerts', `${d.lowStockCount + d.outOfStockCount}`, `${d.outOfStockCount} out · ${d.lowStockCount} low`],
  ].map(([l, v, s]) => `<div class="card kpi"><div class="label">${l}</div><div class="value">${v}</div><div class="sub">${s}</div></div>`).join('');

  $('gstBody').innerHTML = d.gstByRate.length ? d.gstByRate.map(r => `<tr><td><b>${pct(r.gst_rate)}</b></td>
      <td class="num">${r.qty}</td><td class="num">${rs(r.taxable)}</td><td class="num">${rs(r.cgst)}</td>
      <td class="num">${rs(r.sgst)}</td><td class="num">${rs(r.tax)}</td></tr>`).join('')
    : '<tr><td colspan="6" class="muted">INFO-REP-001 No bills yet today</td></tr>';
  $('payModes').innerHTML = d.payments.map(p => `<span class="badge info" style="font-size:.85rem">${esc(p.mode)}: ${rs0(p.amount)} (${p.count})</span>`).join('') || '<span class="muted small">—</span>';

  drawBars(d.last30Days);
  $('topBody').innerHTML = d.topVariants.map(v => `<tr><td>${esc(v.name)} <span class="muted small">${esc(v.size)}/${esc(v.colour)}</span></td>
      <td class="num">${v.qty} pcs</td><td class="num">${rs0(v.value)}</td></tr>`).join('') || '<tr><td class="muted">No sales yet</td></tr>';

  $('lowCounts').innerHTML = `<span class="badge err">${d.outOfStockCount} out</span> <span class="badge warn">${d.lowStockCount} low</span>`;
  $('lowBody').innerHTML = d.lowStock.map(v => `<tr><td>${esc(v.name)} <span class="muted small">${esc(v.size)}/${esc(v.colour)}</span></td>
      <td class="small">${esc(v.sku)}</td><td class="num">${v.stock_qty === 0 ? '<span class="badge err">0</span>' : '<span class="badge warn">' + v.stock_qty + '</span>'}</td>
      <td class="num">${v.reorder_level}</td></tr>`).join('');

  $('alerts').innerHTML = d.openAlerts.length ? d.openAlerts.map(a => `<div class="msg ${a.severity === 'ERROR' ? 'err' : a.severity === 'WARN' ? 'warn' : 'info'} row between">
      <span><span class="code">${esc(a.alert_type)}</span>${esc(a.message)} <span class="small muted">${fmtDateTime(a.created_at)}</span></span>
      <button class="small" data-ack="${a.alert_id}">OK</button></div>`).join('')
    : '<div class="muted small">No open alerts. New low-stock / out-of-stock alerts appear here after sales.</div>';
  $('alerts').querySelectorAll('[data-ack]').forEach(b => b.onclick = async () => {
    await api('/alerts/' + b.dataset.ack + '/resolve', { method: 'POST' });
    load();
  });

  $('billsBody').innerHTML = d.recentBills.map(b => `<tr class="clickable" data-id="${b.sale_id}">
      <td><b>${esc(b.invoice_no)}</b> ${b.status !== 'COMPLETED' ? '<span class="badge warn">' + esc(b.status) + '</span>' : ''}<div class="small muted">${fmtDateTime(b.completed_at)} · ${esc(b.cashier)}</div></td>
      <td>${esc(b.customer_name || 'Walk-in')}</td><td class="num">${rs0(b.grand_total)}</td></tr>`).join('')
    || '<tr><td class="muted">No bills yet</td></tr>';
  $('billsBody').querySelectorAll('[data-id]').forEach(r => r.onclick = () => window.open('receipt.html?id=' + r.dataset.id + '&dup=1', '_blank'));
}

function drawBars(rows) {
  const byDay = Object.fromEntries(rows.map(r => [String(r.day).slice(0, 10), Number(r.sales)]));
  const days = [];
  for (let i = 29; i >= 0; i--) days.push(isoDate(new Date(Date.now() - i * 86400000)));
  const max = Math.max(1, ...days.map(d => byDay[d] || 0));
  $('bars').innerHTML = days.map((d, i) => `<div class="bar ${i === 29 ? 'today' : ''}" style="height:${((byDay[d] || 0) / max) * 100}%"
      title="${fmtDate(d)}: ${rs0(byDay[d] || 0)}"></div>`).join('');
  $('barFrom').textContent = fmtDate(days[0]);
}

function applyPreset() {
  const now = new Date();
  let from = new Date(now);
  switch ($('preset').value) {
    case 'today': break;
    case 'week': from.setDate(now.getDate() - ((now.getDay() + 6) % 7)); break;
    case 'month': from = new Date(now.getFullYear(), now.getMonth(), 1); break;
    case 'fy': from = new Date(now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1, 3, 1); break;
    default: return;
  }
  $('from').value = isoDate(from);
  $('to').value = isoDate(now);
}

async function runReport() {
  clearMsg($('reportMsg'));
  try {
    const r = await api(`/reports/sales?from=${$('from').value}&to=${$('to').value}`);
    const t = r.totals;
    if (!Number(t.bills)) {
      $('report').innerHTML = '<div class="msg info"><span class="code">INFO-REP-001</span>No transactions found for the selected period.</div>';
      return;
    }
    $('report').innerHTML = `<div class="grid cols-3" style="align-items:start">
      <table><tbody>
        <tr><td>Bills</td><td class="num">${t.bills}</td></tr>
        <tr><td>Gross sales</td><td class="num">${rs(t.gross)}</td></tr>
        <tr><td>Discounts</td><td class="num">−${rs(t.discounts)}</td></tr>
        <tr><td><b>Net sales</b></td><td class="num"><b>${rs(t.net_sales)}</b></td></tr>
        <tr><td>Returns (credit notes)</td><td class="num">−${rs(t.returns_value)}</td></tr>
        <tr><td><b>Net after returns</b></td><td class="num"><b>${rs(t.net_after_returns)}</b></td></tr>
      </tbody></table>
      <table><thead><tr><th>GST</th><th class="num">Taxable</th><th class="num">CGST</th><th class="num">SGST</th></tr></thead><tbody>
        ${r.gstByRate.map(g => `<tr><td>${pct(g.gst_rate)}</td><td class="num">${rs(g.taxable)}</td><td class="num">${rs(g.cgst)}</td><td class="num">${rs(g.sgst)}</td></tr>`).join('')}
      </tbody></table>
      <table><thead><tr><th>Payment</th><th class="num">Amount</th></tr></thead><tbody>
        ${r.payments.map(p => `<tr><td>${esc(p.mode)} (${p.count})</td><td class="num">${rs(p.amount)}</td></tr>`).join('')}
      </tbody></table></div>
      <h3 style="margin-top:1rem">Day by day</h3>
      <div class="table-wrap"><table><thead><tr><th>Date</th><th class="num">Bills</th><th class="num">Gross</th><th class="num">Discount</th><th class="num">Taxable</th><th class="num">GST</th><th class="num">Net sales</th></tr></thead>
      <tbody>${r.daily.map(d => `<tr><td>${fmtDate(d.day)}</td><td class="num">${d.bills}</td><td class="num">${rs(d.gross)}</td><td class="num">${rs(d.discounts)}</td>
        <td class="num">${rs(d.taxable)}</td><td class="num">${rs(d.gst)}</td><td class="num">${rs(d.net_sales)}</td></tr>`).join('')}</tbody></table></div>`;
  } catch (err) { showError($('reportMsg'), err); }
}
