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

    function applyFile(file) {
        if (!file.name.toLowerCase().endsWith('.xlsx')) {
            alert('Only .xlsx files are supported.');
            return;
        }
        const dt = new DataTransfer();
        dt.items.add(file);
        hiddenFile.files = dt.files;
        fileNameEl.textContent = '📄 ' + file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
        uploadBtn.disabled = false;
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
