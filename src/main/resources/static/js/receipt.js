// Renders a saved bill. Used by receipt.html (thermal print) and ebill.html (link sent on WhatsApp).
function renderReceipt(r, { duplicate = false } = {}) {
  const saved = r.lines.reduce((s, l) => s + Number(l.unitDiscount) * l.quantity, 0);
  const statusNote = r.status === 'CANCELLED'
    ? `<div class="r-center r-bold r-void">*** VOID – CANCELLED ***<div class="r-small">${esc(r.voidReason || '')} · ${fmtDateTime(r.voidedAt)}</div></div>`
    : r.status !== 'COMPLETED' ? `<div class="r-center r-bold">${esc(r.status.replace('_', ' '))}</div>` : '';
  return `
  <div class="r-center">
    <div class="r-shop">${esc(r.shop.name)}</div>
    <div>${esc(r.shop.address)}</div>
    <div>Ph ${esc(r.shop.phone)} · GSTIN ${esc(r.shop.gstin)}</div>
    <div class="r-title">TAX INVOICE${duplicate ? ' – DUPLICATE' : ''}</div>
  </div>
  ${statusNote}
  <div class="r-row"><span>Invoice</span><b>${esc(r.invoiceNo)}</b></div>
  <div class="r-row"><span>Date</span><span>${fmtDateTime(r.completedAt)}</span></div>
  <div class="r-row"><span>Cashier: ${esc(r.cashier)}</span></div>
  ${r.customerName ? `<div class="r-row"><span>Customer: ${esc(r.customerName)} (${esc(r.customerMobile)})</span></div>` : ''}
  <div class="r-sub r-row"><span>Item</span><span>Amount</span></div>
  ${r.lines.map(l => `<div class="r-item">
      <div><b>${esc(l.productName)}</b> ${esc(l.size)}/${esc(l.colour)}</div>
      <div class="r-row"><span>${l.quantity} x ${inr.format(l.unitPrice)}</span><span>${inr.format(Number(l.unitPrice) * l.quantity)}</span></div>
      ${Number(l.unitDiscount) > 0 ? `<div class="r-row r-small"><span>Disc ${l.offerName ? esc(l.offerName) : ''}</span><span>−${inr.format(Number(l.unitDiscount) * l.quantity)}</span></div>` : ''}
      <div class="r-small">HSN ${esc(l.hsnCode)} · GST ${pct(l.gstRate)}${l.returnedQty ? ' · returned ' + l.returnedQty : ''}</div>
    </div>`).join('')}
  <table class="r-table r-sum">
    <tr><td>Gross</td><td class="num">${inr.format(r.subtotal)}</td></tr>
    ${Number(r.discountTotal) > 0 ? `<tr><td>Discount</td><td class="num">−${inr.format(r.discountTotal)}</td></tr>` : ''}
    <tr><td>Taxable value</td><td class="num">${inr.format(r.taxableTotal)}</td></tr>
    <tr><td>CGST</td><td class="num">${inr.format(r.cgstTotal)}</td></tr>
    <tr><td>SGST</td><td class="num">${inr.format(r.sgstTotal)}</td></tr>
    <tr><td>Round-off</td><td class="num">${inr.format(r.roundOff)}</td></tr>
    <tr class="r-grand"><td>TOTAL</td><td class="num">₹${inr.format(r.grandTotal)}</td></tr>
  </table>
  <div class="r-sub">GST summary</div>
  <table class="r-table">
    <thead><tr><th>Rate</th><th class="num">Taxable</th><th class="num">CGST</th><th class="num">SGST</th></tr></thead>
    <tbody>${r.taxSummary.map(t => `<tr><td>${pct(t.rate)}</td><td class="num">${inr.format(t.taxable)}</td>
      <td class="num">${inr.format(t.cgst)}</td><td class="num">${inr.format(t.sgst)}</td></tr>`).join('')}</tbody>
  </table>
  <div class="r-sub">Paid</div>
  ${r.payments.map(p => `<div class="r-row"><span>${esc(p.mode.replace('_', ' '))}${p.reference ? ' (' + esc(p.reference) + ')' : ''}</span><span>${inr.format(p.amount)}</span></div>`).join('')}
  ${r.creditNotes.length ? `<div class="r-sub">Returns</div>` + r.creditNotes.map(c =>
      `<div class="r-row"><span>${esc(c.cnNo)} (${esc(c.reason || '')})</span><span>−${inr.format(c.amount)}</span></div>`).join('') : ''}
  ${saved > 0 ? `<div class="r-center r-bold" style="margin-top:6px">You saved ₹${inr.format(saved)}!</div>` : ''}
  <div class="r-center r-small" style="margin-top:8px">Exchange within 7 days with this bill. Innerwear, socks, blouses,
    nightwear and bridal wear are not returnable. Prices include GST.</div>
  <div class="r-center" style="margin-top:6px">Thank you! Visit again 🙏</div>`;
}
