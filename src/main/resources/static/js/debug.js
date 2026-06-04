window.authHeader = { 'Authorization': `Bearer ${window.token}`, ...window.csrfHeader };
window.jsonHeader = { 'Content-Type': 'application/json', ...authHeader };

const RUNNER_HEADER =
`import java.util.*;

public class DebugRunner {

    @org.junit.jupiter.api.Test
    void run() throws Exception {
`;
const RUNNER_LINE_OFFSET = RUNNER_HEADER.split('\n').length - 1; // → 6

const RUNNER_FOOTER = `    }\n}\n`;

function wrapRunnerCode(body) {
    return RUNNER_HEADER + body + RUNNER_FOOTER;
}

function runnerDisplayLine(serverLine) {
    return serverLine - RUNNER_LINE_OFFSET;
}

window.editors = { monaco: {}, restricted: {} };

window.editors.monaco.debug = monaco.editor.create(
    document.getElementById('monaco-container-debug'), {
        value: '',
        language: 'java',
        theme: 'vs-dark',
        automaticLayout: true,
        fontSize: 16,
        glyphMargin: true,
    }
);

window.editors.monaco.runner = monaco.editor.create(
    document.getElementById('monaco-container-runner'), {
        value: '',
        language: 'java',
        theme: 'vs-dark',
        automaticLayout: true,
        fontSize: 14,
        glyphMargin: true,
        minimap: { enabled: false },
    }
);

let currentComponent = null;

function makeRunnerTemplate(className) {
    return `// Call ${className} methods here — runs top to bottom:\n${className} obj = new ${className}(/* args */);\n`;
}

document.getElementById('component-select').addEventListener('change', function () {
    if (this.value) loadComponent(this.value);
});

async function loadComponent(name) {
    currentComponent = name;
    window.editors.monaco.debug.setValue('// loading…');
    window.editors.monaco.runner.setValue('// loading…');
    _hideStepper();
    renderStatus('');

    const [cutRes, mainRes] = await Promise.all([
        fetch(`${apiUrl}/components/${name}/cut/src`, { headers: authHeader }),
        fetch(`${apiUrl}/components/${name}/debug/main`, { headers: authHeader }),
    ]);
    if (!cutRes.ok) { renderStatus('<p class="clr-error">Failed to load class source.</p>'); return; }
    const cutData = await cutRes.json();
    window.editors.monaco.debug.setValue(cutData.sourceCode);
    window.cutClassName = cutData.className;
    window.testClassName = 'DebugRunner';

    const runnerCode = mainRes.ok
        ? (await mainRes.json()).code
        : makeRunnerTemplate(cutData.className);
    window.editors.monaco.runner.setValue(runnerCode);

    document.getElementById('editor-execute-btn').disabled = false;
    document.getElementById('editor-reset-cut-btn').style.display = 'block';
}

async function saveCut() {
    if (!currentComponent) return;
    const statusInfo = document.getElementById('editor-status-text');
    statusInfo.innerText = 'Saving…';
    await fetch(`${apiUrl}/components/${currentComponent}/cut/src`, {
        method: 'PUT',
        headers: jsonHeader,
        body: JSON.stringify({ code: window.editors.monaco.debug.getValue() }),
    }).then(r => { statusInfo.innerText = r.ok ? 'Saved!' : 'Save failed'; })
      .catch(() => { statusInfo.innerText = 'Save failed'; });
    setTimeout(() => { statusInfo.innerText = ''; }, 3000);
}

const execBtn = document.getElementById('editor-execute-btn');

const execute = async () => {
    if (!currentComponent) { renderStatus('<p class="clr-error">No component selected.</p>'); return; }
    renderStatus('<p>Running…</p>');
    execBtn.disabled = true;

    await saveCut();

    const code = wrapRunnerCode(window.editors.monaco.runner.getValue());

    const res = await fetch(`${apiUrl}/components/${currentComponent}/debug/execute`, {
        method: 'POST',
        headers: jsonHeader,
        body: JSON.stringify({ code }),
    }).catch(e => {
        console.error(e);
        renderStatus('<p class="clr-error">Network error.</p>');
        execBtn.disabled = false;
        return null;
    });

    if (!res) return;
    execBtn.disabled = false;

    if (res.status === 401) {
        renderStatus('<p class="clr-error">Session expired. <a href="/login">Login again.</a></p>');
        return;
    }
    if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'Unknown error' }));
        renderStatus(`<p class="clr-error"><strong>Execution failed.</strong></p>
                      <pre class="clr-error">${_e(err.message ?? '')}</pre>`);
        return;
    }

    const obj = await res.json();
    console.log('Execution result:', obj);
    handleExecutionResult(obj);
};

execBtn.addEventListener('click', execute);

function handleExecutionResult(obj) {
    renderLogs(obj.logs);
    renderDebugTrace(obj.debugTrace);

    if (obj.testStatus === 'PASSED') {
        renderStatus('');
        return;
    }

    let r = '';
    for (const details of Object.values(obj.testDetails)) {
        if (details.trace) {
            r += `<span class="clr-error">Exception:</span><pre class="clr-error">${_e(details.trace)}</pre>`;
        } else if (details.accessDenied) {
            r += `<span class="clr-error">Access denied: ${_e(details.accessDenied)}</span>`;
        } else if (details.testStatus !== 'PASSED') {
            r += `<span class="clr-error">Run failed (${_e(details.testStatus)})</span>`;
        }
    }
    if (r) renderStatus(r);
}

document.getElementById('editor-reset-cut-btn').addEventListener('click', async () => {
    if (!currentComponent) return;
    if (!confirm('Reset the class to its original version? Your edits will be lost.')) return;
    const res = await fetch(`${apiUrl}/components/${currentComponent}/cut/reset`, {
        headers: authHeader, method: 'POST',
    });
    if (res.ok) {
        window.editors.monaco.debug.setValue((await res.json()).sourceCode);
    } else {
        alert('Reset failed.');
    }
});

document.addEventListener('keydown', ev => {
    const ctrl = ev.ctrlKey || ev.metaKey;
    if (ctrl && ev.key === 's')  { ev.preventDefault(); if (currentComponent) saveCut(); }
    if (ev.key === 'F10')        { ev.preventDefault(); if (currentComponent) execute(); }
    if (!_debugSteps) return;
    if (ev.key === 'ArrowLeft')  { ev.preventDefault(); debugStepPrev(); }
    if (ev.key === 'ArrowRight') { ev.preventDefault(); debugStepNext(); }
    if (ev.key === 'F8')         { ev.preventDefault(); debugContinue(); }
});

const statusEl = document.getElementById('execution-result');

function _relayoutEditors() {
    const containers = document.querySelectorAll('.monaco-editor-container');
    containers.forEach(el => el.style.height = '0');
    window.editors.monaco.debug.layout();
    window.editors.monaco.runner.layout();
    containers.forEach(el => el.style.height = 'initial');
}

function renderStatus(content) {
    statusEl.innerHTML = content;
    if (content) {
        document.getElementById('bottom-panel').setAttribute('aria-hidden', 'false');
        switchTab('results');
        setTimeout(() => document.getElementById('bottom-panel').scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 50);
    }
    _relayoutEditors();
}

const _e = (() => {
    const enc = document.createElement('textarea');
    return (s) => { enc.innerText = s ?? ''; return enc.innerHTML; };
})();

function _renderRow(name, node) {
    const label = `<span class="var-name">${_e(String(name))}</span><span class="var-eq">=</span>`;
    const preview = node?.preview ?? '';
    const children = node?.children;

    if (children && Object.keys(children).length > 0) {
        return `<details class="var-tree"><summary class="debug-var-row">` +
            `<span class="var-tree-arrow"></span>${label}<code class="var-val">${_e(preview)}</code></summary>` +
            `<div class="var-tree-body">` +
            Object.entries(children).map(([k, v]) => _renderRow(k, v)).join('') +
            `</div></details>`;
    }

    return `<div class="debug-var-row">` +
        `<span class="var-tree-spacer"></span>${label}<code class="var-val">${_e(preview)}</code></div>`;
}

function renderLogs(logs) {
    const id = (window.cutClassName ?? '') + '#' + window.userId;
    const decos = (logs?.[id] ?? []).map(log => ({
        range: new monaco.Range(log.lineNumber, 1, log.lineNumber, 1),
        options: {
            isWholeLine: true,
            className: 'log',
            glyphMarginClassName: 'log-glyph',
            hoverMessage: { value: `<i>${log.testMethodName}:</i> ${log.message}`, supportHtml: true },
        },
    }));
    window.logDecorations = window.editors.monaco.debug.deltaDecorations(
        window.logDecorations ?? [], decos
    );
}

/** @type {Array|null} */
let _debugSteps = null;
let _debugStepIndex = 0;

const _breakpoints   = { debug: new Set(), test: new Set() };
const _bpDecorations = { debug: [], test: [] };

function _toggleBreakpoint(editorKey, lineNumber) {
    const editor = window.editors.monaco[editorKey];
    if (!editor) return;
    _breakpoints[editorKey].has(lineNumber)
        ? _breakpoints[editorKey].delete(lineNumber)
        : _breakpoints[editorKey].add(lineNumber);
    _renderBreakpointDecorations(editorKey);
}

function _renderBreakpointDecorations(editorKey) {
    const editor = window.editors.monaco[editorKey];
    if (!editor) return;
    _bpDecorations[editorKey] = editor.deltaDecorations(
        _bpDecorations[editorKey],
        [..._breakpoints[editorKey]].map(line => ({
            range: new monaco.Range(line, 1, line, 1),
            options: {
                glyphMarginClassName: 'debug-breakpoint-glyph',
                glyphMarginHoverMessage: { value: 'Breakpoint – click to remove' },
                stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
            },
        }))
    );
}

(function _attachBreakpointHandlers() {
    const M = monaco.editor.MouseTargetType;
    const gutter = new Set([M.GUTTER_GLYPH_MARGIN, M.GUTTER_LINE_NUMBERS, M.GUTTER_LINE_DECORATIONS]);
    window.editors.monaco.debug.onMouseDown(e => {
        if (gutter.has(e.target.type) && e.target.position?.lineNumber)
            _toggleBreakpoint('debug', e.target.position.lineNumber);
    });
    window.editors.monaco.runner.onMouseDown(e => {
        if (gutter.has(e.target.type) && e.target.position?.lineNumber)
            _toggleBreakpoint('test', e.target.position.lineNumber);
    });
})();

function renderDebugTrace(debugTrace) {
    const cutId    = (window.cutClassName  ?? '') + '#' + window.userId;
    const runnerId = (window.testClassName ?? '') + '#' + window.userId;

    const cutSteps    = (debugTrace?.[cutId]    ?? []).map(s => ({ ...s, _source: 'cut'    }));
    const runnerSteps = (debugTrace?.[runnerId] ?? []).map(s => ({ ...s, _source: 'runner' }));

    const all = [...cutSteps, ...runnerSteps].sort((a, b) => a.globalIndex - b.globalIndex);
    _debugSteps     = all.length > 0 ? all : null;
    _debugStepIndex = 0;

    if (!_debugSteps) { _hideStepper(); return; }
    document.getElementById('bottom-panel').setAttribute('aria-hidden', 'false');
    switchTab('debugger');
    setTimeout(() => document.getElementById('bottom-panel').scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 50);
    _relayoutEditors();
    _renderCurrentStep();
}

function _visibleLine(step) {
    return step._source === 'runner'
        ? runnerDisplayLine(step.lineNumber)
        : step.lineNumber;
}

function _renderCurrentStep() {
    if (!_debugSteps) return;
    const step     = _debugSteps[_debugStepIndex];
    const isCut    = step._source === 'cut';
    const dispLine = _visibleLine(step);

    document.getElementById('debug-step-label').innerHTML =
        `<span class="debug-source-badge ${isCut ? 'cut' : 'test'}">${_e(isCut ? 'Class' : 'Runner')}</span>` +
        `Step ${_debugStepIndex + 1}&thinsp;/&thinsp;${_debugSteps.length}` +
        `&ensp;&middot;&ensp;Line ${dispLine}` +
        `&ensp;&middot;&ensp;${_e(step.methodName)}`;

    document.getElementById('debug-prev-btn').disabled     = _debugStepIndex === 0;
    document.getElementById('debug-next-btn').disabled     = _debugStepIndex === _debugSteps.length - 1;
    document.getElementById('debug-continue-btn').disabled = _debugStepIndex === _debugSteps.length - 1;

    const entries = Object.entries(step.variables ?? {});
    document.getElementById('debug-vars-body').innerHTML = entries.length === 0
        ? '<div class="no-vars">No variables in scope yet</div>'
        : entries.map(([k, v]) => _renderRow(k, v)).join('');

    _highlightDebugLine(dispLine, step._source);
}

function debugStepPrev() {
    if (_debugSteps && _debugStepIndex > 0) { _debugStepIndex--; _renderCurrentStep(); }
}
function debugStepNext() {
    if (_debugSteps && _debugStepIndex < _debugSteps.length - 1) { _debugStepIndex++; _renderCurrentStep(); }
}
function debugContinue() {
    if (!_debugSteps) return;
    for (let i = _debugStepIndex + 1; i < _debugSteps.length; i++) {
        const step = _debugSteps[i];
        const editorKey = step._source === 'cut' ? 'debug' : 'test';
        if (_breakpoints[editorKey].has(_visibleLine(step))) {
            _debugStepIndex = i; _renderCurrentStep(); return;
        }
    }
    _debugStepIndex = _debugSteps.length - 1;
    _renderCurrentStep();
}

const _currentLineDecors = { debug: [], test: [] };

function _highlightDebugLine(visibleLine, source) {
    const editorMap = { debug: window.editors.monaco.debug, test: window.editors.monaco.runner };
    ['debug', 'test'].forEach(key => {
        const editor = editorMap[key];
        if (!editor) return;
        const isActive = (key === 'test') === (source !== 'cut');
        const decos = (isActive && visibleLine > 0) ? [{
            range: new monaco.Range(visibleLine, 1, visibleLine, 1),
            options: {
                isWholeLine: true,
                className: 'debug-current-line',
                glyphMarginClassName: 'debug-current-line-glyph',
            },
        }] : [];
        _currentLineDecors[key] = editor.deltaDecorations(_currentLineDecors[key], decos);
    });
    if (visibleLine > 0) {
        const activeEditor = source === 'cut' ? window.editors.monaco.debug : window.editors.monaco.runner;
        activeEditor?.revealLineInCenter(visibleLine);
    }
}

function _hideStepper() {
    _highlightDebugLine(0, 'cut');
    _highlightDebugLine(0, 'runner');
    document.getElementById('debug-prev-btn').disabled     = true;
    document.getElementById('debug-next-btn').disabled     = true;
    document.getElementById('debug-continue-btn').disabled = true;
    document.getElementById('debug-step-label').textContent = 'Run to see debug trace';
    document.getElementById('debug-vars-body').innerHTML   =
        '<div class="no-vars">No trace available yet</div>';
}

function switchTab(name) {
    ['debugger', 'results'].forEach(t => {
        document.getElementById(`tab-btn-${t}`).classList.toggle('active', t === name);
        document.getElementById(`tab-pane-${t}`).setAttribute('aria-hidden', t === name ? 'false' : 'true');
    });
}

_hideStepper();