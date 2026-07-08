/**
 * viewer-grid.js
 * Local validation issues grid — no API calls, no external libraries.
 * Data comes from JG_ISSUES (JSON array embedded by Thymeleaf at page load).
 *
 * Features:
 *  - Inline text search across all columns
 *  - Severity filter pills (All / Critical / Warning)
 *  - Click-to-sort on any column (asc / desc toggle)
 *  - Row count display
 *  - Client-side CSV export of visible rows
 *  - Animated row entry
 */

(function () {
    'use strict';

    // ── State ────────────────────────────────────────────────────────────────
    let jgData        = [];   // full processed data
    let jgFiltered    = [];   // after severity + text filter
    let jgSortCol     = null;
    let jgSortDir     = 'asc';
    let jgActiveFilter= 'all';
    let jgSearchTerm  = '';

    // ── Init ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        if (typeof JG_ISSUES === 'undefined' || !Array.isArray(JG_ISSUES)) return;

        jgData = JG_ISSUES.map(function (d, i) {
            return {
                idx:       i,
                ruleId:    d.ruleId    || '',
                severity:  d.severity  || '',
                sheetName: d.sheetName || '',
                row:       (d.rowIdx != null && d.rowIdx >= 0)
                               ? 'Row ' + (d.rowIdx + 1)
                               : 'Multiple',
                rowNum:    (d.rowIdx != null && d.rowIdx >= 0) ? d.rowIdx : 9999,
                fieldName: d.fieldName || '',
                message:   d.message   || ''
            };
        });

        updateCounts();
        jgRender();

        // Keyboard shortcut: / to focus search
        document.addEventListener('keydown', function (e) {
            if (e.key === '/' && document.activeElement !== document.getElementById('jgSearch')) {
                e.preventDefault();
                document.getElementById('jgSearch').focus();
            }
        });
    });

    // ── Public functions (called from inline onclick) ─────────────────────────
    window.jgFilter    = jgFilter;
    window.jgSetFilter = jgSetFilter;
    window.jgSort      = jgSort;
    window.jgExportCsv = jgExportCsv;

    function jgFilter() {
        jgSearchTerm = (document.getElementById('jgSearch').value || '').toLowerCase();
        jgRender();
    }

    function jgSetFilter(severity, btn) {
        jgActiveFilter = severity;
        document.querySelectorAll('.jg-pill').forEach(function (p) {
            p.classList.remove('jg-pill-active');
        });
        btn.classList.add('jg-pill-active');
        jgRender();
    }

    function jgSort(col) {
        if (jgSortCol === col) {
            jgSortDir = jgSortDir === 'asc' ? 'desc' : 'asc';
        } else {
            jgSortCol = col;
            jgSortDir = 'asc';
        }
        // Update sort icons
        document.querySelectorAll('.jg-sort-icon').forEach(function (ic) {
            ic.className = 'fa fa-sort jg-sort-icon';
        });
        var th = document.querySelector('[data-col="' + col + '"] .jg-sort-icon');
        if (th) th.className = 'fa fa-sort-' + (jgSortDir === 'asc' ? 'up' : 'down') + ' jg-sort-icon jg-sort-active';
        jgRender();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    function jgRender() {
        // 1. Severity filter
        jgFiltered = jgData.filter(function (d) {
            if (jgActiveFilter === 'all') return true;
            return d.severity === jgActiveFilter;
        });

        // 2. Text search
        if (jgSearchTerm) {
            jgFiltered = jgFiltered.filter(function (d) {
                return [d.ruleId, d.severity, d.sheetName, d.row, d.fieldName, d.message]
                    .some(function (v) { return v.toLowerCase().includes(jgSearchTerm); });
            });
        }

        // 3. Sort
        if (jgSortCol) {
            var sortKey = jgSortCol === 'row' ? 'rowNum' : jgSortCol;
            jgFiltered.sort(function (a, b) {
                var av = a[sortKey], bv = b[sortKey];
                if (typeof av === 'number') return jgSortDir === 'asc' ? av - bv : bv - av;
                av = String(av).toLowerCase(); bv = String(bv).toLowerCase();
                return jgSortDir === 'asc'
                    ? av.localeCompare(bv)
                    : bv.localeCompare(av);
            });
        }

        // 4. Build rows
        var tbody = document.getElementById('jgBody');
        var empty = document.getElementById('jgEmpty');
        tbody.innerHTML = '';

        if (jgFiltered.length === 0) {
            empty.style.display = '';
        } else {
            empty.style.display = 'none';
            jgFiltered.forEach(function (d, i) {
                var tr = document.createElement('tr');
                tr.className = 'jg-row ' + (d.severity === 'CRITICAL' ? 'jg-row-crit' : 'jg-row-warn');
                tr.style.animationDelay = (i * 18) + 'ms';

                // Rule badge
                var badgeCls = d.severity === 'CRITICAL' ? 'jg-badge jg-badge-crit' : 'jg-badge jg-badge-warn';
                tr.innerHTML =
                    '<td><span class="' + badgeCls + '">' + esc(d.ruleId) + '</span></td>' +
                    '<td><span class="jg-sev jg-sev-' + d.severity.toLowerCase() + '">' +
                        '<i class="fa ' + (d.severity === 'CRITICAL' ? 'fa-circle-xmark' : 'fa-triangle-exclamation') + '"></i> ' +
                        esc(d.severity) + '</span></td>' +
                    '<td>' + esc(d.sheetName) + '</td>' +
                    '<td>' + esc(d.row) + '</td>' +
                    '<td><code class="jg-code">' + esc(d.fieldName) + '</code></td>' +
                    '<td class="jg-msg">' + highlight(esc(d.message), jgSearchTerm) + '</td>';
                tbody.appendChild(tr);
            });
        }

        // Row count
        document.getElementById('jgRowCount').textContent =
            jgFiltered.length + ' of ' + jgData.length + ' issue' + (jgData.length !== 1 ? 's' : '');
    }

    function updateCounts() {
        var crit = jgData.filter(function (d) { return d.severity === 'CRITICAL'; }).length;
        var warn = jgData.filter(function (d) { return d.severity === 'WARNING'; }).length;
        document.getElementById('jgCountAll').textContent  = jgData.length;
        document.getElementById('jgCountCrit').textContent = crit;
        document.getElementById('jgCountWarn').textContent = warn;
    }

    // ── CSV export ────────────────────────────────────────────────────────────
    function jgExportCsv() {
        var rows = [['Rule ID','Severity','Sheet','Row','Field','Message']];
        jgFiltered.forEach(function (d) {
            rows.push([d.ruleId, d.severity, d.sheetName, d.row, d.fieldName, d.message]);
        });
        var csv = rows.map(function (r) {
            return r.map(function (c) {
                return '"' + String(c).replace(/"/g, '""') + '"';
            }).join(',');
        }).join('\r\n');

        var blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        var url  = URL.createObjectURL(blob);
        var a    = document.createElement('a');
        a.href   = url;
        a.download = 'validation-issues.csv';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    function esc(s) {
        return String(s || '')
            .replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function highlight(text, term) {
        if (!term) return text;
        var re = new RegExp('(' + term.replace(/[.*+?^${}()|[\]\\]/g,'\\$&') + ')', 'gi');
        return text.replace(re, '<mark class="jg-highlight">$1</mark>');
    }
}());
