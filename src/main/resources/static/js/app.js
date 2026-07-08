/* ═══════════════════════════════════════════════════════════════════════════
   XYZ Excel Viewer — Frontend JS
   Handles: drag-drop upload, sheet tab switching, issues panel toggle
═══════════════════════════════════════════════════════════════════════════ */

/* ── Drag & Drop Upload ────────────────────────────────────────────────── */
(function initUpload() {
    const dropZone   = document.getElementById('dropZone');
    const fileInput  = document.getElementById('fileInput');
    const hiddenFile = document.getElementById('hiddenFile');
    const uploadBtn  = document.getElementById('uploadBtn');
    const fileNameEl = document.getElementById('fileName');
    const uploadForm = document.getElementById('uploadForm');
    const ruleError = document.getElementById('ruleValidationError');

    if (!dropZone) return;

    dropZone.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) applyFile(fileInput.files[0]);
    });

    dropZone.addEventListener('dragover', e => {
        e.preventDefault(); dropZone.classList.add('dragover');
    });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
    dropZone.addEventListener('drop', e => {
        e.preventDefault(); dropZone.classList.remove('dragover');
        if (e.dataTransfer.files[0]) applyFile(e.dataTransfer.files[0]);
    });


    uploadForm.addEventListener('submit', function (e) {

        const checkedRules =
            document.querySelectorAll('input[name="rules"]:checked');

        if (checkedRules.length === 0) {

            e.preventDefault();

            if (ruleError) {
                ruleError.style.display = 'block';
            }

            return false;
        }

        if (ruleError) {
            ruleError.style.display = 'none';
        }
    });


    document
        .querySelectorAll('input[name="rules"]')
        .forEach(cb => {

            cb.addEventListener('change', () => {

                const checked =
                    document.querySelectorAll(
                        'input[name="rules"]:checked'
                    );

                if (checked.length > 0 && ruleError) {
                    ruleError.style.display = 'none';
                }
            });
        });


    function applyFile(file) {
        if (!file.name.toLowerCase().endsWith('.xlsx')) {
            alert('Only .xlsx files are supported.');
            return;
        }
        const dt = new DataTransfer();
        dt.items.add(file);
        hiddenFile.files = dt.files;
        fileNameEl.textContent = '📄 ' + file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
//        uploadBtn.disabled = false;
        updateUploadButtonState();
    }
})();

/* ── Sheet Tab Switching ───────────────────────────────────────────────── */
function showSheet(idx) {
    // Hide all panels
    document.querySelectorAll('.sheet-panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.sheet-tab').forEach(b => b.classList.remove('active'));

    // Show selected
    const panel = document.getElementById('sheet-' + idx);
    const btn   = document.getElementById('tab-btn-' + idx);
    if (panel) panel.classList.add('active');
    if (btn)   btn.classList.add('active');

    // Update info bar
    updateSheetInfo(idx);
}

function updateSheetInfo(idx) {
    const infoEl = document.getElementById('sheetInfo');
    if (!infoEl || typeof SHEET_DATA === 'undefined') return;
    // SHEET_DATA is injected from Thymeleaf — use sheet stats from DOM instead
    const panel = document.getElementById('sheet-' + idx);
    if (!panel) return;
    const stats = panel.querySelector('.sheet-stats');
    if (stats && infoEl) infoEl.textContent = '';
}

/* ── Issues Panel Toggle ──────────────────────────────────────────────── */
function toggleIssues() {
    const body    = document.getElementById('issuesBody');
    const chevron = document.getElementById('issuesChevron');
    if (!body) return;
    const open = body.style.display !== 'none';
    body.style.display = open ? 'none' : 'block';
    if (chevron) chevron.className = open ? 'fa fa-chevron-right' : 'fa fa-chevron-down';
}

/* ── Active nav highlight ──────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname;
    document.querySelectorAll('.nav-link').forEach(link => {
        const href = link.getAttribute('href');
        if (href && href !== '/' && path.startsWith(href)) {
            link.style.background = 'rgba(255,255,255,.2)';
        }
    });

    // Keep issues panel open if there are errors
    const issuesBody = document.getElementById('issuesBody');
    if (issuesBody) {
        // default open
        issuesBody.style.display = 'block';
    }

    // Tooltip positioning — flip if near bottom of viewport
    document.querySelectorAll('.excel-cell').forEach(cell => {
        cell.addEventListener('mouseenter', function() {
            const tooltip = this.querySelector('.cell-tooltip');
            if (!tooltip) return;
            const rect = this.getBoundingClientRect();
            const spaceBelow = window.innerHeight - rect.bottom;
            if (spaceBelow < 120) {
                tooltip.style.top = 'auto';
                tooltip.style.bottom = '100%';
            } else {
                tooltip.style.top = '100%';
                tooltip.style.bottom = 'auto';
            }
        });
    });
});

/* ── Export dropdown toggle ─────────────────────────────────────────────── */
function toggleDropdown(btn) {
    const menu = btn.nextElementSibling;
    menu.classList.toggle('open');
    // Close on outside click
    setTimeout(() => {
        document.addEventListener('click', function close(e) {
            if (!btn.parentElement.contains(e.target)) {
                menu.classList.remove('open');
                document.removeEventListener('click', close);
            }
        });
    }, 0);
}


function showLoader(title, message) {

    document.getElementById("loaderTitle")
        .innerText = title;

    document.getElementById("loaderMessage")
        .innerText = message;

    document.getElementById("globalLoader")
        .style.display = "flex";
}

function hideLoader() {

    document.getElementById("globalLoader")
        .style.display = "none";
}

/* ── Rule tabs (sheet-tabbed validation rules) ─────────────────────────────
   Turns each `.rule-tabset` (a set of `.rule-group` blocks) into a tabbed
   panel: one tab per sheet, only the active sheet's rules visible. Used on
   both the home upload card and the viewer "Edit Rules" modal. */
function buildRuleTabs(tabset) {
    if (!tabset || tabset.__tabbed) return;
    var groups = Array.prototype.slice.call(
        tabset.querySelectorAll(':scope > .rule-group'));
    if (groups.length === 0) return;

    var bar = document.createElement('div');
    bar.className = 'rule-tabbar';

    groups.forEach(function (g, i) {
        var sheet  = g.getAttribute('data-sheet')  || ('Sheet ' + (i + 1));
        var locked = g.getAttribute('data-locked') === 'true';

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'rule-tab' + (i === 0 ? ' active' : '');
        btn.innerHTML =
            '<i class="fa fa-table-list" aria-hidden="true"></i>' +
            '<span class="rt-name">' + sheet + '</span>' +
            (locked
                ? '<span class="rt-count rt-lock" title="Always applied"><i class="fa fa-lock"></i></span>'
                : '<span class="rt-count">0/0</span>');
        btn.addEventListener('click', function () { activateRuleTab(tabset, i); });
        bar.appendChild(btn);

        g.classList.add('rule-tabpanel');
        if (i !== 0) g.classList.add('rt-hidden');
    });

    tabset.insertBefore(bar, tabset.firstChild);
    tabset.__tabbed = true;
    refreshRuleTabCounts(tabset);
}

function activateRuleTab(tabset, idx) {
    tabset.querySelectorAll(':scope > .rule-tabbar > .rule-tab')
        .forEach(function (b, i) { b.classList.toggle('active', i === idx); });
    tabset.querySelectorAll(':scope > .rule-tabpanel')
        .forEach(function (p, i) { p.classList.toggle('rt-hidden', i !== idx); });
}

function refreshRuleTabCounts(scope) {
    var tabsets;
    if (scope && scope.classList && scope.classList.contains('rule-tabset')) {
        tabsets = [scope];
    } else {
        tabsets = Array.prototype.slice.call(
            (scope || document).querySelectorAll('.rule-tabset'));
    }
    tabsets.forEach(function (tabset) {
        var panels = tabset.querySelectorAll(':scope > .rule-tabpanel');
        var tabs   = tabset.querySelectorAll(':scope > .rule-tabbar > .rule-tab');
        panels.forEach(function (p, i) {
            var tab = tabs[i];
            if (!tab) return;
            var cnt = tab.querySelector('.rt-count');
            if (!cnt || cnt.classList.contains('rt-lock')) return;
            var total   = p.querySelectorAll('input[name="rules"]:not([disabled])').length;
            var checked = p.querySelectorAll('input[name="rules"]:not([disabled]):checked').length;
            cnt.textContent = checked + '/' + total;
            tab.classList.toggle('rt-empty', checked === 0);
        });
    });
}

window.buildRuleTabs       = buildRuleTabs;
window.activateRuleTab     = activateRuleTab;
window.refreshRuleTabCounts = refreshRuleTabCounts;

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.rule-tabset').forEach(buildRuleTabs);
    refreshRuleTabCounts();
});
