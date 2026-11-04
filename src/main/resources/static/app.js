const $ = s => document.querySelector(s);
const inr = new Intl.NumberFormat('en-IN', {style: 'currency', currency: 'INR'});
const inr0 = new Intl.NumberFormat('en-IN', {style: 'currency', currency: 'INR', maximumFractionDigits: 0});
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const pad = n => String(n).padStart(2, '0');
const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
let meta, timer;

async function api(url, opts) {
  const r = await fetch(url, opts);
  const d = r.status === 204 ? null : await r.json().catch(() => ({}));
  if (!r.ok) throw d || {};
  return d;
}
const post = (url, body) => api(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
const errMsg = e => (e.details && e.details.join(' · ')) || e.error || 'Something went wrong. Check that the app is still running.';

function toast(msg, warn) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast show' + (warn ? ' warn' : '');
  clearTimeout(timer);
  timer = setTimeout(() => t.className = 'toast', 4500);
}

const fill = (sel, list) => sel.innerHTML = list.map(c => `<option>${esc(c)}</option>`).join('');
const onType = () => fill($('#category'), $('#type').value === 'Expense' ? meta.expense : meta.income);
function defaults() {
  $('#date').value = $('#date').max = $('#rcDate').value = $('#rcDate').max = ymd(new Date());
  onType();
}

function showAlert(a) {
  const over = a.level === 'exceeded', box = $('#alertBox');
  $('#alertTitle').textContent = over ? 'Budget exceeded' : 'Budget almost used up';
  $('#alertMsg').textContent = over
    ? `You have spent ${inr.format(a.spent)} on ${a.category} this month. That is ${inr.format(a.spent - a.limit)} over your ${inr.format(a.limit)} budget.`
    : `You have used ${Math.round(a.spent / a.limit * 100)}% of your ${a.category} budget (${inr.format(a.spent)} of ${inr.format(a.limit)}).`;
  box.className = a.level;
  box.showModal();
  $('#alertOk').focus();
}
$('#alertOk').addEventListener('click', () => $('#alertBox').close());

function renderReceipts(r) {
  const a = r.analysis;
  $('#rcLine').textContent = a.count
    ? `${a.count} receipt${a.count > 1 ? 's' : ''} this month · ${inr.format(a.total)} total · ${inr.format(a.average)} average`
    : 'No receipts this month. Save one with the form.';
  $('#rcShops').innerHTML = a.by_shop.map(s => `<div class="bud">
    <div class="top"><span>${esc(s.shop)} (${s.count})</span><span>${inr.format(s.total)}</span></div>
    <div class="track"><div class="fill" style="width:${s.total / a.by_shop[0].total * 100}%"></div></div></div>`).join('');
  $('#rcLargest').textContent = a.largest.length
    ? 'Largest: ' + a.largest.map(x => `${x.shop} ${inr.format(x.amount)}`).join(' · ') : '';
  $('#gallery').innerHTML = r.items.map(x => `<figure class="card">
    <a href="/api/receipts/file/${encodeURIComponent(x.file_name)}" target="_blank" rel="noopener">
      <img src="/api/receipts/file/${encodeURIComponent(x.file_name)}" alt="Receipt from ${esc(x.shop)}" loading="lazy"></a>
    <figcaption><b>${esc(x.shop)}</b><span>${esc(x.date)} · ${esc(x.category)}</span><span>${inr.format(x.amount)}</span>
    <button class="ghost" data-tx="${x.transaction_id}">Delete</button></figcaption></figure>`).join('');
}

function renderSummary(s) {
  $('#income').textContent = inr.format(s.income);
  $('#expense').textContent = inr.format(s.expense);
  $('#savings').textContent = inr.format(s.savings);
  $('#rate').textContent = s.savings_rate + '%';
  const max = Math.max(1, ...s.trend.flatMap(t => [t.income, t.expense]));
  $('#trend').innerHTML = s.trend.map(t => `<div class="col"><div class="bars">
    <i class="in" style="height:${t.income / max * 100}%" title="Income ${inr.format(t.income)}"></i>
    <i class="out" style="height:${t.expense / max * 100}%" title="Expenses ${inr.format(t.expense)}"></i>
  </div><div class="lbl">${esc(t.label)}<b title="Savings in ${esc(t.month)}">${inr0.format(t.savings)}</b></div></div>`).join('');
}

function renderBudgets(list) {
  const note = x => ({ok: 'On track', warning: 'Close to your limit', exceeded: 'Over budget by ' + inr.format(x.spent - x.limit)}[x.status]);
  $('#budgets').innerHTML = list.length ? list.map(x => `<div class="bud ${x.status}">
    <div class="top"><span>${esc(x.category)}</span><span>${inr.format(x.spent)} of ${inr.format(x.limit)}</span></div>
    <div class="track"><div class="fill" style="width:${Math.min(x.pct, 100)}%"></div></div>
    <small>${esc(note(x))}</small></div>`).join('')
    : '<p class="empty">No budgets yet. Choose a category and a monthly limit to start.</p>';
}

function renderRows(list) {
  $('#none').hidden = list.length > 0;
  $('#rows').innerHTML = list.map(t => `<tr>
    <td>${esc(t.date)}</td><td>${esc(t.description || '—')}</td><td>${esc(t.category)}</td>
    <td class="amt">${t.type === 'Income' ? '+' : '−'} ${inr.format(t.amount)}</td>
    <td><button class="ghost" data-id="${t.id}" aria-label="Delete transaction">Delete</button></td></tr>`).join('');
}

async function load() {
  const m = $('#month').value, ty = $('#filter').value;
  if (!m) return;
  try {
    const [s, b, tx, rc] = await Promise.all([
      api(`/api/summary?month=${m}`), api(`/api/budgets?month=${m}`),
      api(`/api/transactions?month=${m}${ty ? '&type=' + ty : ''}`), api(`/api/receipts?month=${m}`)]);
    renderSummary(s); renderBudgets(b); renderRows(tx); renderReceipts(rc);
    $('#chart').src = `/api/chart.png?month=${m}&t=${Date.now()}`;
  } catch (e) { toast(errMsg(e), true); }
}

$('#txForm').addEventListener('submit', async e => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  data.amount = parseFloat(data.amount);
  try {
    const r = await post('/api/transactions', data);
    toast('Transaction added');
    if (r.alert) showAlert(r.alert);
    e.target.reset(); defaults();
    $('#month').value = data.date.slice(0, 7);
    load();
  } catch (err) { toast(errMsg(err), true); }
});

$('#budForm').addEventListener('submit', async e => {
  e.preventDefault();
  const d = Object.fromEntries(new FormData(e.target));
  try {
    await post('/api/budgets', {category: d.category, monthly_limit: parseFloat(d.monthly_limit), warning_threshold: parseFloat(d.pct) / 100});
    toast('Budget saved'); e.target.monthly_limit.value = ''; load();
  } catch (err) { toast(errMsg(err), true); }
});

$('#rows').addEventListener('click', async e => {
  const id = e.target.dataset.id;
  if (!id) return;
  try { await api('/api/transactions/' + id, {method: 'DELETE'}); toast('Transaction deleted'); load(); }
  catch (err) { toast(errMsg(err), true); }
});

$('#rcForm').addEventListener('submit', async e => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const r = await api('/api/receipts', {method: 'POST', body: fd});
    toast('Receipt saved');
    if (r.alert) showAlert(r.alert);
    $('#month').value = fd.get('date').slice(0, 7);
    e.target.reset(); defaults(); load();
  } catch (err) { toast(errMsg(err), true); }
});

$('#gallery').addEventListener('click', async e => {
  const id = e.target.dataset.tx;
  if (!id) return;
  try { await api('/api/transactions/' + id, {method: 'DELETE'}); toast('Receipt and expense deleted'); load(); }
  catch (err) { toast(errMsg(err), true); }
});

$('#type').addEventListener('change', onType);
$('#month').addEventListener('change', load);
$('#filter').addEventListener('change', load);

(async () => {
  try {
    meta = await api('/api/meta');
    fill($('#budCategory'), meta.expense);
    fill($('#rcCategory'), meta.expense);
    defaults();
    $('#month').value = ymd(new Date()).slice(0, 7);
    load();
  } catch (e) { toast(errMsg(e), true); }
})();
