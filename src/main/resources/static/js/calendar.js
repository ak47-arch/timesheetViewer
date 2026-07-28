/* Leave Calendar — vanilla JS, no dependencies.
 * Renders a Mon–Sun month grid and merges three event kinds from
 * GET /calendar/events: HOLIDAY, MY_LEAVE, TEAM_LEAVE.
 * Self-service register (POST /calendar/leave) and cancel
 * (POST /calendar/leave/delete/{id}). CSRF token read from <body> data.
 */
(function () {
  "use strict";

  const body = document.body;
  const state = {
    year:  parseInt(body.dataset.year, 10),
    month: parseInt(body.dataset.month, 10), // 1-12
    canRegister: body.dataset.canRegister === "true",
    csrf: body.dataset.csrf || "",
    csrfHeader: body.dataset.csrfHeader || "X-CSRF-TOKEN",
    events: []
  };

  const MONTHS = ["January","February","March","April","May","June",
                  "July","August","September","October","November","December"];

  const grid   = document.getElementById("calGrid");
  const label  = document.getElementById("calLabel");
  const modal  = document.getElementById("leaveModal");

  // ── date helpers ───────────────────────────────────────────────────────────
  const iso = (d) => {
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${d.getFullYear()}-${m}-${day}`;
  };
  const parseIso = (s) => { const [y,m,d] = s.split("-").map(Number); return new Date(y, m-1, d); };
  const sameYmd = (a,b) => a.getFullYear()===b.getFullYear() && a.getMonth()===b.getMonth() && a.getDate()===b.getDate();

  // Monday-based offset (Mon=0 … Sun=6)
  const mondayIndex = (jsDay) => (jsDay + 6) % 7;

  function gridRange() {
    const first = new Date(state.year, state.month - 1, 1);
    const start = new Date(first);
    start.setDate(first.getDate() - mondayIndex(first.getDay()));
    const last = new Date(state.year, state.month, 0); // last day of month
    const end = new Date(last);
    end.setDate(last.getDate() + (6 - mondayIndex(last.getDay())));
    return { start, end };
  }

  // ── data ───────────────────────────────────────────────────────────────────
  async function load() {
    const { start, end } = gridRange();
    label.textContent = `${MONTHS[state.month - 1]} ${state.year}`;
    try {
      const res = await fetch(`/calendar/events?from=${iso(start)}&to=${iso(end)}`, { headers: { "Accept": "application/json" } });
      state.events = res.ok ? await res.json() : [];
    } catch (e) {
      state.events = [];
    }
    render(start, end);
  }

  function eventsByDate() {
    const map = {};
    for (const ev of state.events) (map[ev.date] = map[ev.date] || []).push(ev);
    return map;
  }

  // ── render grid ──────────────────────────────────────────────────────────────
  function render(start, end) {
    const byDate = eventsByDate();
    const today = new Date();
    grid.innerHTML = "";

    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const cell = document.createElement("div");
      cell.className = "cal-day";
      const inMonth = d.getMonth() === state.month - 1;
      const dow = d.getDay();
      const weekend = dow === 0 || dow === 6;
      if (!inMonth) cell.classList.add("out");
      if (weekend)  cell.classList.add("weekend");
      if (sameYmd(d, today)) cell.classList.add("today");

      const key = iso(d);
      const num = document.createElement("div");
      num.className = "cal-day-num";
      num.textContent = d.getDate();
      cell.appendChild(num);

      const evs = byDate[key] || [];
      // Order: holidays, my leave, planner leave, team
      const rank = { HOLIDAY: 0, MY_LEAVE: 1, TEAM_LEAVE: 2 };
      evs.sort((a,b) => (rank[a.kind]-rank[b.kind]));
      for (const ev of evs) cell.appendChild(chip(ev));

      // Clicking an in-month cell opens the leave modal pre-filled.
      if (state.canRegister && inMonth) {
        cell.classList.add("clickable");
        cell.addEventListener("click", (e) => {
          if (e.target.classList.contains("chip-x")) return; // handled separately
          openModal(key, key);
        });
      }
      grid.appendChild(cell);
    }
    renderSide();
  }

  function chip(ev) {
    const el = document.createElement("div");
    let cls = "chip-team", label = ev.title;
    if (ev.kind === "HOLIDAY") { cls = "chip-holiday"; }
    else if (ev.kind === "MY_LEAVE") { cls = ev.source === "ADMIN_PLANNER" ? "chip-planner" : "chip-mine"; }
    else if (ev.kind === "TEAM_LEAVE") { cls = ev.source === "ADMIN_PLANNER" ? "chip-planner" : "chip-team"; }
    el.className = "chip " + cls;

    const txt = document.createElement("span");
    txt.className = "txt";
    txt.textContent = label;
    txt.title = (ev.subtitle ? ev.title + " · " + ev.subtitle : ev.title);
    el.appendChild(txt);

    if (ev.editable && ev.leaveId != null) {
      const x = document.createElement("span");
      x.className = "chip-x";
      x.textContent = "×";
      x.title = "Cancel this leave";
      x.addEventListener("click", (e) => { e.stopPropagation(); cancelLeave(ev.leaveId); });
      el.appendChild(x);
    }
    return el;
  }

  // ── side panel ───────────────────────────────────────────────────────────────
  function renderSide() {
    const inThisMonth = (dateStr) => {
      const dt = parseIso(dateStr);
      return dt.getFullYear() === state.year && dt.getMonth() === state.month - 1;
    };
    const holidays = state.events.filter(e => e.kind === "HOLIDAY" && inThisMonth(e.date))
                                 .sort((a,b) => a.date.localeCompare(b.date));
    const team = state.events.filter(e => (e.kind === "TEAM_LEAVE" || e.kind === "MY_LEAVE") && inThisMonth(e.date))
                             .sort((a,b) => a.date.localeCompare(b.date));

    const hEl = document.getElementById("sideHolidays");
    hEl.innerHTML = holidays.length ? "" : `<p class="side-empty">No holidays this month.</p>`;
    holidays.forEach(e => {
      const dt = parseIso(e.date);
      hEl.insertAdjacentHTML("beforeend",
        `<div class="side-item"><span class="d">${dt.getDate()} ${MONTHS[dt.getMonth()].slice(0,3)}</span>` +
        `<span class="n">${escapeHtml(e.title)}</span></div>`);
    });

    const tEl = document.getElementById("sideTeam");
    tEl.innerHTML = team.length ? "" : `<p class="side-empty">No team leave this month.</p>`;
    team.forEach(e => {
      const dt = parseIso(e.date);
      const who = e.kind === "MY_LEAVE" ? "You" : e.title;
      tEl.insertAdjacentHTML("beforeend",
        `<div class="side-item"><span class="d">${dt.getDate()} ${MONTHS[dt.getMonth()].slice(0,3)}</span>` +
        `<span class="n">${escapeHtml(who)}</span> <span class="sub">${escapeHtml(e.subtitle || "")}</span></div>`);
    });
  }

  // ── modal ────────────────────────────────────────────────────────────────────
  function openModal(startVal, endVal) {
    document.getElementById("leaveStart").value = startVal || "";
    document.getElementById("leaveEnd").value = endVal || startVal || "";
    document.getElementById("leaveType").value = "FULL_DAY";
    document.getElementById("leaveReason").value = "";
    setMsg("", "");
    showExistingLeave(startVal);
    modal.style.display = "flex";
  }

  // If the user already has a deletable leave on this date, offer a Delete button.
  function showExistingLeave(dateStr) {
    const box = document.getElementById("existingLeave");
    const mine = state.events.find(e =>
      e.date === dateStr && e.kind === "MY_LEAVE" && e.editable && e.leaveId != null);
    if (mine) {
      document.getElementById("existingLeaveText").textContent =
        `You have leave booked on ${dateStr}` + (mine.subtitle ? ` (${mine.subtitle})` : "");
      const del = document.getElementById("existingLeaveDelete");
      del.dataset.id = mine.leaveId;
      box.style.display = "flex";
    } else {
      box.style.display = "none";
    }
  }
  function closeModal() { modal.style.display = "none"; }
  function setMsg(text, kind) {
    const m = document.getElementById("modalMsg");
    m.textContent = text; m.className = "modal-msg" + (kind ? " " + kind : "");
  }

  async function saveLeave() {
    const start = document.getElementById("leaveStart").value;
    let end = document.getElementById("leaveEnd").value;
    if (!start) { setMsg("Pick a start date.", "err"); return; }
    if (!end) end = start;
    if (end < start) { setMsg("End date can't be before start date.", "err"); return; }

    const params = new URLSearchParams({
      startDate: start, endDate: end,
      leaveType: document.getElementById("leaveType").value,
      reason: document.getElementById("leaveReason").value
    });
    const data = await post("/calendar/leave", params);
    if (data && data.ok) {
      setMsg(data.message || "Added.", "ok");
      await load();
      setTimeout(closeModal, 700);
    } else {
      setMsg((data && data.message) || "Could not register leave.", "err");
    }
  }

  async function cancelLeave(id) {
    if (!confirm("Cancel this leave day?")) return;
    const data = await post(`/calendar/leave/delete/${id}`, new URLSearchParams());
    if (data && data.ok) { closeModal(); load(); }
    else alert((data && data.message) || "Could not cancel.");
  }

  async function post(url, params) {
    const headers = { "Content-Type": "application/x-www-form-urlencoded" };
    if (state.csrf) headers[state.csrfHeader] = state.csrf;
    try {
      const res = await fetch(url, { method: "POST", headers, body: params.toString() });
      return await res.json();
    } catch (e) { return { ok: false, message: "Network error." }; }
  }

  function escapeHtml(s) {
    return (s || "").replace(/[&<>"']/g, c =>
      ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[c]));
  }

  // ── nav ──────────────────────────────────────────────────────────────────────
  function shift(delta) {
    let m = state.month + delta, y = state.year;
    if (m < 1) { m = 12; y--; } else if (m > 12) { m = 1; y++; }
    state.month = m; state.year = y; load();
  }

  document.getElementById("calPrev").addEventListener("click", () => shift(-1));
  document.getElementById("calNext").addEventListener("click", () => shift(1));
  document.getElementById("calToday").addEventListener("click", () => {
    const now = new Date(); state.year = now.getFullYear(); state.month = now.getMonth() + 1; load();
  });

  const btnNew = document.getElementById("btnNewLeave");
  if (btnNew) btnNew.addEventListener("click", () => {
    const t = new Date(); openModal(iso(t), iso(t));
  });
  document.getElementById("modalClose").addEventListener("click", closeModal);
  document.getElementById("modalCancel").addEventListener("click", closeModal);
  document.getElementById("modalSave").addEventListener("click", saveLeave);
  document.getElementById("existingLeaveDelete").addEventListener("click", (e) => {
    const id = e.currentTarget.dataset.id;
    if (id) cancelLeave(parseInt(id, 10));
  });
  modal.addEventListener("click", (e) => { if (e.target === modal) closeModal(); });

  load();
})();
