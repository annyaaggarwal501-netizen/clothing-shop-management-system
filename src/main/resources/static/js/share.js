// "Share product" on WhatsApp (REQ-WA-2). wa.me links carry text only, so the app draws a product card
// image in the browser. On a phone it opens the share sheet with image + text (Web Share API);
// on a PC it downloads the image and opens WhatsApp with the text filled in, so staff attach the image.

async function openShare(productId) {
  let data;
  try { data = await api('/whatsapp/product/' + productId); }
  catch (err) { toast(err.message, 'err'); return; }

  const m = openModal(`<h2>Share on WhatsApp</h2>
    <div class="grid cols-2">
      <div class="share-preview"><canvas id="shCanvas" width="720" height="900"></canvas></div>
      <div class="stack">
        <div class="share-text" id="shText"></div>
        <div class="field"><label>Send to an opted-in customer (optional)</label>
          <div class="row"><input type="tel" id="shMobile" placeholder="10-digit mobile" class="grow" style="width:auto"><button id="shFind">Find</button></div>
          <div id="shCust" class="small" style="margin-top:.3rem"></div></div>
        <div id="shErr"></div>
      </div>
    </div>
    <div class="actions">
      <button data-close>Close</button>
      <button id="shDownload">Download image</button>
      <button class="whatsapp" id="shGo">Share</button>
    </div>
    <p class="small muted">Phone: opens the share sheet with picture and text. PC: downloads the picture and opens WhatsApp Web – attach the picture there.</p>`,
    { wide: true });

  let target = null;   // {id, name} once a customer is chosen
  const canvas = m.el.querySelector('#shCanvas');
  drawCard(canvas, data);
  m.el.querySelector('#shText').textContent = data.text;

  m.el.querySelector('#shFind').onclick = async () => {
    const box = m.el.querySelector('#shCust');
    clearMsg(m.el.querySelector('#shErr'));
    try {
      const c = await api('/customers/lookup?mobile=' + encodeURIComponent(m.el.querySelector('#shMobile').value));
      const res = await api(`/whatsapp/product/${productId}?customerId=${c.id}`);
      data = res;
      target = { id: c.id, name: c.name };
      box.innerHTML = `<span class="badge ok">Will send to ${esc(c.name)}</span>`;
    } catch (err) {
      target = null;
      box.innerHTML = '';
      showError(m.el.querySelector('#shErr'), err);
    }
  };

  const fileName = data.name.replace(/[^a-z0-9]+/gi, '_') + '.png';
  const download = () => {
    const a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = fileName;
    a.click();
  };
  m.el.querySelector('#shDownload').onclick = download;

  m.el.querySelector('#shGo').onclick = async () => {
    const blob = await new Promise(r => canvas.toBlob(r, 'image/png'));
    const file = new File([blob], fileName, { type: 'image/png' });
    if (target) {
      // A chosen customer: open their chat directly (consent already checked by the server).
      await api(`/whatsapp/product/${productId}/sent?customerId=${target.id}`, { method: 'POST' }).catch(() => {});
      download();
      window.open(data.waUrl, '_blank');
      return;
    }
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      try { await navigator.share({ files: [file], text: data.text }); return; }
      catch (e) { if (e.name === 'AbortError') return; }
    }
    download();
    window.open(data.waUrl, '_blank');
  };
}

function drawCard(canvas, d) {
  const c = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;
  const g = c.createLinearGradient(0, 0, W, H);
  g.addColorStop(0, '#8a1c40'); g.addColorStop(1, '#3b0b1c');
  c.fillStyle = g; c.fillRect(0, 0, W, H);
  c.fillStyle = '#fff';
  c.beginPath(); c.roundRect(40, 140, W - 80, H - 260, 28); c.fill();

  c.fillStyle = '#fff'; c.textAlign = 'center';
  c.font = 'bold 44px system-ui, sans-serif'; c.fillText('Mansi Collection', W / 2, 80);
  c.font = '26px system-ui, sans-serif'; c.globalAlpha = .85; c.fillText('New arrivals · Kotputli', W / 2, 118); c.globalAlpha = 1;

  c.fillStyle = '#1f2328';
  c.font = 'bold 46px system-ui, sans-serif';
  wrap(c, d.name, W / 2, 230, W - 140, 56);

  c.font = '28px system-ui, sans-serif'; c.fillStyle = '#646b73';
  c.fillText('Sizes', W / 2, 380);
  c.fillStyle = '#1f2328'; c.font = 'bold 32px system-ui, sans-serif';
  wrap(c, d.sizes.join('  ·  '), W / 2, 422, W - 140, 40);
  c.font = '28px system-ui, sans-serif'; c.fillStyle = '#646b73';
  c.fillText('Colours', W / 2, 500);
  c.fillStyle = '#1f2328'; c.font = 'bold 32px system-ui, sans-serif';
  wrap(c, d.colours.join('  ·  '), W / 2, 542, W - 140, 40);

  const price = Number(d.price), offer = d.offerPrice != null ? Number(d.offerPrice) : null;
  if (offer != null) {
    c.fillStyle = '#646b73'; c.font = '36px system-ui, sans-serif';
    const old = '₹' + price.toLocaleString('en-IN');
    c.fillText(old, W / 2, 630);
    const w = c.measureText(old).width;
    c.fillRect(W / 2 - w / 2, 618, w, 3);
    c.fillStyle = '#8a1c40'; c.font = 'bold 76px system-ui, sans-serif';
    c.fillText('₹' + offer.toLocaleString('en-IN'), W / 2, 712);
    c.fillStyle = '#1a7f37'; c.font = 'bold 28px system-ui, sans-serif';
    c.fillText(d.offerName, W / 2, 755);
  } else {
    c.fillStyle = '#8a1c40'; c.font = 'bold 80px system-ui, sans-serif';
    c.fillText('₹' + price.toLocaleString('en-IN'), W / 2, 700);
  }
  c.fillStyle = '#fff'; c.font = '26px system-ui, sans-serif';
  c.fillText('Visit us · Main Bazaar, Kotputli', W / 2, H - 70);
}

function wrap(c, text, x, y, maxW, lh) {
  const words = String(text).split(' ');
  let line = '';
  for (const w of words) {
    const t = line ? line + ' ' + w : w;
    if (c.measureText(t).width > maxW && line) { c.fillText(line, x, y); line = w; y += lh; }
    else line = t;
  }
  c.fillText(line, x, y);
}
