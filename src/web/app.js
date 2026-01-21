const API = "http://localhost:8080";

const $ = (id) => document.getElementById(id);

function outJson(el, obj) {
  el.textContent = (typeof obj === "string") ? obj : JSON.stringify(obj, null, 2);
}

function esc(s) {
  return (s ?? "").toString().replace(/</g,"&lt;").replace(/>/g,"&gt;");
}

async function saveStep() {
  const payload = {
    codeText: $("codeText").value,
    note: $("note").value,
    errorType: $("errorType").value
  };

  const res = await fetch(`${API}/step`, {
    method: "POST",
    headers: {"Content-Type":"application/json"},
    body: JSON.stringify(payload)
  });

  const data = await res.json();
  if (!res.ok) { alert(data.error || "Save failed"); return; }

  $("note").value = "";
  await refresh();
  await loadAnalytics();
}

async function refresh() {
  const res = await fetch(`${API}/timeline`);
  const data = await res.json();
  if (!res.ok) { alert(data.error || "Timeline failed"); return; }

  // current
  outJson($("currentBox"), data.current);

  // timeline list
  const currentId = data.current?.versionId ?? null;
  const wrap = $("timeline");
  wrap.innerHTML = "";

  (data.timeline || []).forEach(v => {
    const div = document.createElement("div");
    div.className = "item" + (v.versionId === currentId ? " active" : "");
    const dt = new Date(v.timestamp).toLocaleString();

    div.innerHTML = `
      <div class="meta">
        <b>v${v.versionId}</b> • ${esc(dt)} • ${v.bugFree ? "✅ bug‑free" : "—"}
      </div>
      <div class="note">${esc(v.note || "(no note)")}</div>
      <div class="tag">${esc(v.errorType || "Unknown")}</div>
      <div class="smallbtns">
        <button data-act="load">Load to Editor</button>
        <button data-act="mark">Mark Bug‑Free</button>
      </div>
    `;

    div.querySelector('[data-act="load"]').onclick = () => {
      $("codeText").value = v.codeText || "";
      $("note").value = v.note || "";
      $("errorType").value = v.errorType || "";
    };

    div.querySelector('[data-act="mark"]').onclick = async () => {
      const r = await fetch(`${API}/markBugFree?id=${encodeURIComponent(v.versionId)}`, { method: "POST" });
      const d = await r.json();
      if (!r.ok) { alert(d.error || "Mark failed"); return; }
      await refresh();
    };

    wrap.appendChild(div);
  });
}

async function undoStep() {
  const res = await fetch(`${API}/undo`, { method: "POST" });
  const data = await res.json();
  if (!res.ok) { alert(data.error || "Undo failed"); return; }
  outJson($("currentBox"), data.current);
  await refresh();
}

async function markBugFree() {
  // mark current version bug-free
  const t = await fetch(`${API}/timeline`);
  const data = await t.json();
  const id = data.current?.versionId;
  if (!id) { alert("No current version to mark"); return; }

  const res = await fetch(`${API}/markBugFree?id=${encodeURIComponent(id)}`, { method: "POST" });
  const d = await res.json();
  if (!res.ok) { alert(d.error || "Mark failed"); return; }
  await refresh();
}

async function jumpBugFree() {
  const res = await fetch(`${API}/jumpBugFree`, { method: "POST" });
  const data = await res.json();
  if (!res.ok) { alert(data.error || "Jump failed"); return; }
  outJson($("currentBox"), data.current);
  await refresh();
}

async function loadAnalytics() {
  const res = await fetch(`${API}/analytics`);
  const data = await res.json();
  if (!res.ok) { alert(data.error || "Analytics failed"); return; }
  outJson($("analyticsBox"), data);
}

// auto-load
refresh();
loadAnalytics();
