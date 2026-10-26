// Bulk product import (REQ-PRD-5): choose a .csv/.xlsx file -> preview every row -> import the good rows.
function openImport() {
  let file = null;
  const m = openModal(`<h2>Import products from CSV / Excel</h2>
    <p class="small muted">Columns: product_name, category, brand, hsn_code, tax_class, size, colour, cost_price, selling_price, mrp,
      opening_stock, barcode (optional: gender, fabric, returnable). One row per size/colour. Rows with the same product name
      and category become one product; an existing product gets the new sizes/colours added. Max 5 MB.
      Templates: <code>db/products_import.csv</code>, <code>db/sample_import_new_products.csv</code>.</p>
    <div class="row"><input type="file" id="impFile" accept=".csv,.xlsx" class="grow" style="width:auto">
      <button class="primary" id="impPreview" disabled>Check file</button></div>
    <div id="impErr" style="margin-top:.6rem"></div>
    <div id="impResult"></div>
    <div class="actions"><button data-close>Close</button><button class="success hidden" id="impGo">Import</button></div>`,
    { wide: true });
  const el = m.el;
  const fileIn = el.querySelector('#impFile');
  fileIn.onchange = () => {
    file = fileIn.files[0] || null;
    el.querySelector('#impPreview').disabled = !file;
    el.querySelector('#impResult').innerHTML = '';
    el.querySelector('#impGo').classList.add('hidden');
  };
  const send = path => { const fd = new FormData(); fd.append('file', file); return api(path, { method: 'POST', body: fd }); };

  el.querySelector('#impPreview').onclick = async () => {
    clearMsg(el.querySelector('#impErr'));
    try {
      const p = await send('/products/import/preview');
      const good = p.valid + p.warnings;
      el.querySelector('#impResult').innerHTML = `
        <div class="row" style="margin:.6rem 0">
          <span class="badge ok">${p.valid} valid</span><span class="badge warn">${p.warnings} warnings</span>
          <span class="badge err">${p.invalid} invalid</span>
          <span class="small muted">→ ${p.newProducts} new product${p.newProducts === 1 ? '' : 's'}, ${p.existingProducts} existing product${p.existingProducts === 1 ? ' gets' : 's get'} new sizes/colours, ${p.newVariants} variants in all</span>
          <label class="checkbox small" style="margin-left:auto"><input type="checkbox" id="impProblems" ${p.invalid + p.warnings ? 'checked' : ''}> Show only rows with problems</label>
        </div>
        <div class="table-wrap" style="max-height:50vh;overflow-y:auto"><table>
          <thead><tr><th>Row</th><th>Status</th><th>Product</th><th>Size / colour</th><th class="num">Price</th><th class="num">Opening</th><th>Messages</th></tr></thead>
          <tbody>${p.rows.map(r => `<tr data-status="${r.status}">
            <td>${r.rowNo}</td>
            <td><span class="badge ${r.status === 'VALID' ? 'ok' : r.status === 'WARNING' ? 'warn' : 'err'}">${r.status}</span></td>
            <td>${esc(r.productName)} ${r.newProduct && r.status !== 'INVALID' ? '<span class="badge info">new</span>' : ''}<div class="small muted">${esc(r.category)}</div></td>
            <td>${esc(r.size)} / ${esc(r.colour)}</td><td class="num">${r.sellingPrice != null ? rs0(r.sellingPrice) : ''}</td>
            <td class="num">${r.openingStock ?? ''}</td>
            <td class="small">${r.messages.map(x => {
              const c = x.match(/^((?:ERR|WARN)-[A-Z]+-\d+)\s(.*)$/);
              return '<div>' + (c ? '<span class="code">' + c[1] + '</span>' + esc(c[2]) : esc(x)) + '</div>';
            }).join('')}</td></tr>`).join('')}</tbody></table></div>`;
      const filter = () => {
        const only = el.querySelector('#impProblems').checked;
        el.querySelectorAll('tr[data-status]').forEach(tr => tr.classList.toggle('hidden', only && tr.dataset.status === 'VALID'));
      };
      el.querySelector('#impProblems').onchange = filter;
      filter();
      const go = el.querySelector('#impGo');
      go.textContent = `Import ${good} row${good === 1 ? '' : 's'}` + (p.invalid ? ` (skip ${p.invalid} invalid)` : '');
      go.classList.toggle('hidden', good === 0);
    } catch (err) { showError(el.querySelector('#impErr'), err); }
  };

  el.querySelector('#impGo').onclick = async () => {
    const go = el.querySelector('#impGo');
    go.disabled = true;
    try {
      const r = await send('/products/import/commit');
      m.close();
      toast(`Imported: ${r.productsCreated} new products, ${r.productsUpdated} updated, ${r.variantsCreated} variants, `
        + `${r.openingPieces} pieces opening stock${r.rowsSkipped ? ', ' + r.rowsSkipped + ' rows skipped' : ''}`, 'ok');
      if (typeof load === 'function') load();
    } catch (err) {
      go.disabled = false;
      showError(el.querySelector('#impErr'), err);
    }
  };
}
