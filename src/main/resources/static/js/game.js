window.authHeader = {'Authorization': `Bearer ${window.token}`, ...window.csrfHeader};
window.jsonHeader = {'Content-Type': 'application/json', ...authHeader};

/** @type {string|false} */
let currentComponent;
/** @type {UserGameProgressionDTO | false} */
let gameProgress = false;
const loadingText = "loading...";
const minLineCoverage = 50; // % of lines that need to be covered to activate tests
/** @type {Map<string, ComponentData>} */
const componentData = new Map();
/** @type {{monaco:Record<string, ICodeEditor>, restricted:Record<string, constrainedEditor>}} */
window.editors = {monaco: {}, restricted: {}};
const monacoContainerDebug = document.getElementById('monaco-container-debug');
window.editors.monaco.debug = monaco.editor.create(monacoContainerDebug, {
    value: '',
    language: 'java',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 16,
    glyphMargin: true,
});

const monacoContainerTest = document.getElementById('monaco-container-test');
window.editors.monaco.test = monaco.editor.create(monacoContainerTest, {
    value: '',
    language: 'java',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 16,
    glyphMargin: true,
});

let _runnerLineOffset = 0;

function isDebugStrand() {
    return gameProgress?.mode === 'Debugging';
}

function makeRunnerTemplate(className) {
    return `// Call ${className} methods here — runs top to bottom:\n${className} obj = new ${className}(/* args */);\n`;
}

// Load the stored runner body for a component; null when none exists (caller falls back to the template).
async function loadRunner(componentName) {
    const res = await fetch(`${apiUrl}/components/${componentName}/debug/main`, {headers: authHeader});
    return res.ok ? (await res.json()).code : null;
}

// top right buttons: add confirm dialogue to anchors
document.getElementById('reset-game-button').addEventListener('click', ev => {
    ev.preventDefault();
    Popup.instance.open('reset').addButton(
        'Reset',
        () => window.location.replace(isDebugStrand() ? '/reset-debug' : '/reset'),
        ['clr-error']
    );
});
document.getElementById('logout-button').addEventListener('click', ev => {
    ev.preventDefault();
    Popup.instance.open('logout').addButton(
        'Logout',
        () => window.location.replace('/logout'),
        ['clr-error']
    );
});

const uiOverlay = document.getElementById('ui-overlay');

const result = document.getElementById('execution-result');

function renderResult(content) {
    result.innerHTML = content;
    if (content) {
        document.getElementById('bottom-panel').setAttribute('aria-hidden', 'false');
        switchTab('results');
        if (_lastCoverage) _applyCoverageDecorations();
        setTimeout(() => result.scrollIntoView({behavior: 'smooth', block: 'nearest'}), 50);
    }
    const editorContainers = document.querySelectorAll('.monaco-editor-container');
    editorContainers.forEach(el => el.style.height = '0');
    editors.monaco.test.layout();
    editors.monaco.debug.layout();
    editorContainers.forEach(el => el.style.height = 'initial');
    requestAnimationFrame(() => {
        editors.monaco.test.layout();
        editors.monaco.debug.layout();
    });
}

function sessionExpired(statusInfo) {
    renderResult(`<p class="clr-error">Your session has expired.
                <a href="/login" target="_blank" rel="noopener">Login again.</a></p>`);
    statusInfo.innerText = "Save Failed";
}

async function save(componentName = currentComponent, skipRunner = false) {
    if (!currentComponent || window.editors.monaco.test.getValue() === loadingText) {
        console.log('No component loaded, not saving')
        return;
    }

    let noSaveFailure = true;
    const statusInfo = document.getElementById('editor-status-text');
    statusInfo.innerText = "Saving...";
    const data = componentData.get(componentName);

    // 1 ─ Save test (skipped in the debug strand)
    if (!isDebugStrand() && (gameProgress?.status === 'TEST' || gameProgress?.status === 'DEBUGGING')) {
        const test = window.editors.monaco.test.getValue();
        await fetch(`${apiUrl}/components/${componentName}/test/src`, {
            method: 'PUT',
            headers: jsonHeader,
            body: JSON.stringify({code: test}),
        }).then(res => {
            if (res.status === 401) {
                sessionExpired(statusInfo);
                return;
            }
            noSaveFailure &= res.ok;
        }).catch(e => {
            console.error(e);
            noSaveFailure = false;
        });

        // 1.2 ─ Update in local cache
        data.test.sourceCode = test;
    }

    // 1b ─ Save runner (debug strand only; skipped when /debug/execute persists it anyway)
    if (isDebugStrand() && !skipRunner) {
        const runner = window.editors.monaco.test.getValue();
        await fetch(`${apiUrl}/components/${componentName}/debug/main`, {
            method: 'PUT',
            headers: jsonHeader,
            body: JSON.stringify({code: runner}),
        }).then(res => {
            if (res.status === 401) {
                sessionExpired(statusInfo);
                return;
            }
            noSaveFailure &= res.ok;
        }).catch(e => {
            console.error(e);
            noSaveFailure = false;
        });
    }

    // 2 ─ Save cut (only in debug mode, after a component was mutated)
    if (gameProgress?.status === 'DEBUGGING') {
        const cut = window.editors.monaco.debug.getValue();
        await fetch(`${apiUrl}/components/${componentName}/cut/src`, {
            method: 'PUT',
            headers: jsonHeader,
            body: JSON.stringify({code: cut}),
        }).then(res => {
            if (res.status === 401) {
                sessionExpired(statusInfo);
                return;
            }
            noSaveFailure &= res.ok;
        }).catch(e => {
            console.error(e);
            noSaveFailure = false;
        });

        // 2.2 ─ Update in local cache
        data.cut.sourceCode = cut;
    }

    // 3 ─ Update component data
    componentData.set(componentName, data);

    // 4 ─ Update save button state
    if (noSaveFailure) {
        statusInfo.innerText = "Saved!";
    } else {
        statusInfo.innerText = "Save Failed";
    }
    setTimeout(() => {
        statusInfo.innerText = "";
    }, 3e3);
}

/**
 * @param {Array<Range>} editableRanges
 * @param {string} editorName
 */
function constrain(editableRanges, editorName = 'test') {
    const editor = editors.monaco[editorName];
    let constrainedInstance;

    if (editors.restricted[editorName]) {
        constrainedInstance = editors.restricted[editorName];
        // _isRestrictedModel disappears with disposeRestrictions; removing twice throws
        if (editor.getModel()._isRestrictedModel) {
            constrainedInstance.removeRestrictionsIn(editor.getModel());
        }
    } else {
        constrainedInstance = constrainedEditor(monaco);
        editors.restricted[editorName] = constrainedInstance;
        constrainedInstance.initializeIn(editor);
    }

    constrainedInstance.addRestrictionsTo(editor.getModel(), editableRanges.map(range => ({
        range: [range.startLine, range.startColumn, range.endLine, range.endColumn],
        allowMultiline: true
    })));
}

let _lastCoverage = null;
let _debugRevealCoverage = false;

function renderCoverage(coverage) {
    _lastCoverage = coverage;
    _applyCoverageDecorations();
}

// Incremental cache for _revealedCoverage: visit counts of the CUT lines covered by
// steps[0.._reachedCache.upTo]. Counts (not a set) so stepping backwards over one visit
// keeps lines that are still reached by an earlier step.
let _reachedCache = null;

// Coverage restricted to the CUT lines reached up to the current debug step
function _revealedCoverage() {
    const cutClassId = window.cutClassName + '#' + window.userId;
    const full = _lastCoverage?.[cutClassId] ?? {};
    if (!_debugSteps) return {[cutClassId]: {}};

    if (_reachedCache?.steps !== _debugSteps) { // new trace loaded
        _reachedCache = {steps: _debugSteps, upTo: -1, counts: new Map()};
    }
    const c = _reachedCache;
    while (c.upTo < _debugStepIndex) {
        c.upTo++;
        const s = _debugSteps[c.upTo];
        if (s._source !== 'test') { // only CUT lines carry coverage
            c.counts.set(s.lineNumber, (c.counts.get(s.lineNumber) ?? 0) + 1);
        }
    }
    while (c.upTo > _debugStepIndex) {
        const s = _debugSteps[c.upTo];
        if (s._source !== 'test') {
            const n = c.counts.get(s.lineNumber) - 1;
            if (n === 0) c.counts.delete(s.lineNumber);
            else c.counts.set(s.lineNumber, n);
        }
        c.upTo--;
    }

    const result = {};
    for (const [line, count] of Object.entries(full)) {
        if (c.counts.has(parseInt(line))) result[line] = count;
    }
    return {[cutClassId]: result};
}

// (Re)draw coverage line decorations in the class editor, honoring the toggle and step reveal
function _applyCoverageDecorations() {
    const model = window.editors.monaco.debug.getModel();
    if (!model) return;
    const cutClassId = window.cutClassName + '#' + window.userId;
    const source = !_coverageHighlightOn ? null
        : _debugRevealCoverage ? _revealedCoverage()
            : _lastCoverage;
    const linesVisited = Object.entries(source?.[cutClassId] ?? {});
    const decorations = linesVisited.map(cov => {
        const line = parseInt(cov[0]);
        return {
            range: new monaco.Range(line, 1, line, 1),
            options: {
                isWholeLine: true,
                className: 'covered covered-' + cov[1],
                glyphMarginClassName: 'covered-glyph',
            }
        };
    });

    window.cutDecorations = model.deltaDecorations(
        window.cutDecorations ?? [],
        decorations
    );
}

function renderCoveragePercentage(data) {
    const coverageInfo = document.getElementById('coverage-info');
    if (!coverageInfo) return;
    if (!data) {
        coverageInfo.innerHTML = `
            To ensure that your tests can catch as many potential bugs as possible, 
            you should aim for a coverage of at least ${minLineCoverage}%.`;
    } else {
        const {covered, total, percentage, minimum} = data;
        coverageInfo.innerHTML = `<strong>Your current coverage: ${covered} of ${total} lines (${percentage}%)</strong><br>` + (
            percentage >= minimum
                ? `This exceeds the minimum coverage of ${minimum}%. You can activate your tests now.`
                : `To ensure that your tests can catch as many potential bugs as possible, you should aim for a coverage of at least ${minimum}%.`
        );
    }
    window.objectiveDisplay.hide();
}

/**
 * @param {Object<string, Array<LogEntry>>} logs
 */
function renderLogs(logs) {
    const cutClassId = window.cutClassName + '#' + window.userId;
    const data = logs?.[cutClassId] ?? [];
    const decorations = [];
    for (const log of data) {
        const range = new monaco.Range(log.lineNumber, 1, log.lineNumber, 1);
        const decoration = {
            range,
            options: {
                isWholeLine: true,
                className: 'log',
                glyphMarginClassName: 'log-glyph',
                hoverMessage: {
                    value: `<i>${log.testMethodName}:</i> ${log.message}`,
                    supportHtml: true
                },
            }
        };
        decorations.push(decoration);
    }
    window.logDecorations = window.editors.monaco.debug.deltaDecorations(
        window.logDecorations ?? [],
        decorations
    );
}

let _debugSteps = null;
let _debugStepIndex = 0;

const _breakpoints = {debug: new Set(), test: new Set()};
const _bpDecorations = {debug: [], test: []};

// Display lines that carry a LineNumberTable entry, per editor, from the last run.
// Only these lines ever produce a DebugStep, so only these can hold a breakpoint.
const _executableLines = {debug: null, test: null};

// Take over the executable lines of a run result and re-snap the existing breakpoints,
// which may have been placed before any run, i.e. without this information.
function _updateExecutableLines(executableLines) {
    const byEditor = {
        debug: executableLines?.[window.cutClassName + '#' + window.userId],
        test: executableLines?.[window.testClassName + '#' + window.userId],
    };
    ['debug', 'test'].forEach(key => {
        const lines = byEditor[key];
        if (!lines) return;
        _executableLines[key] = new Set(lines.map(l => _toDisplayLine(l, key)));
        const snapped = new Set([..._breakpoints[key]].map(l => _snapBreakpointLine(key, l)));
        _breakpoints[key] = snapped;
        _renderBreakpointDecorations(key);
    });
}

// javac reports a multi-line statement (an if spanning several lines, a chained call, ...)
// on the line it starts on, so a breakpoint on any of its later lines could never hit.
// Move such a breakpoint up to the statement it belongs to.
function _snapBreakpointLine(editorKey, lineNumber) {
    const lines = _executableLines[editorKey];
    if (!lines || lines.has(lineNumber)) return lineNumber;
    let snapped = null;
    for (const line of lines) {
        if (line < lineNumber && (snapped === null || line > snapped)) snapped = line;
    }
    return snapped ?? lineNumber;
}

// Fire-and-forget analytics for debugger UI interactions (breakpoints, buttons).
// Recorded server-side via POST /track; failures are ignored so tracking never blocks the UI.
function trackClientEvent(eventType, details = {}) {
    try {
        fetch(`${apiUrl}/track`, {
            method: 'POST',
            headers: jsonHeader,
            keepalive: true,
            body: JSON.stringify({
                eventType,
                details: {
                    component: currentComponent || null,
                    strand: isDebugStrand() ? 'debug' : 'test',
                    room: gameProgress?.room ?? null,
                    stage: gameProgress?.stage ?? null,
                    status: gameProgress?.status ?? null,
                    ...details,
                },
            }),
        }).catch(() => {});
    } catch (e) { /* never let tracking break the UI */ }
}
window.trackClientEvent = trackClientEvent;

// Same as trackClientEvent, but only ever fires in the debug strand; no-op in testing.
function trackDebugEvent(eventType, details = {}) {
    if (isDebugStrand()) trackClientEvent(eventType, details);
}

// Add/remove a breakpoint on a line and redraw the gutter markers
function _toggleBreakpoint(editorKey, clickedLine) {
    const editor = window.editors.monaco[editorKey];
    if (!editor) return;
    const lineNumber = _snapBreakpointLine(editorKey, clickedLine);
    const bp = _breakpoints[editorKey];
    const details = {editor: editorKey, line: lineNumber};
    if (lineNumber !== clickedLine) details.snappedFrom = clickedLine;
    if (bp.has(lineNumber)) {
        bp.delete(lineNumber);
        trackClientEvent('debug-breakpoint-removed', details);
    } else {
        bp.add(lineNumber);
        trackClientEvent('debug-breakpoint-added', details);
    }
    _renderBreakpointDecorations(editorKey);
}

// Redraw all breakpoint glyphs for one editor
function _renderBreakpointDecorations(editorKey) {
    const editor = window.editors.monaco[editorKey];
    if (!editor) return;
    const newDecos = [..._breakpoints[editorKey]].map(line => ({
        range: new monaco.Range(line, 1, line, 1),
        options: {
            glyphMarginClassName: 'debug-breakpoint-glyph',
            glyphMarginHoverMessage: {value: 'Breakpoint – click to remove'},
            stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
        }
    }));
    _bpDecorations[editorKey] = editor.deltaDecorations(_bpDecorations[editorKey], newDecos);
}

// Wire gutter clicks in both editors to breakpoint toggling
function _attachBreakpointHandlers() {
    const M = monaco.editor.MouseTargetType;
    const gutterTypes = new Set([
        M.GUTTER_GLYPH_MARGIN,
        M.GUTTER_LINE_NUMBERS,
        M.GUTTER_LINE_DECORATIONS,
    ]);
    ['debug', 'test'].forEach(key => {
        const editor = window.editors.monaco[key];
        if (!editor) return;
        editor.onMouseDown(e => {
            const t = e.target;
            if (gutterTypes.has(t.type)) {
                const line = t.position?.lineNumber;
                if (line) _toggleBreakpoint(key, line);
            }
        });
    });
}

_attachBreakpointHandlers();

// Merge class + test steps in execution order and start the stepper
function renderDebugTrace(debugTrace) {
    const cutClassId = window.cutClassName + '#' + window.userId;
    const testClassId = window.testClassName + '#' + window.userId;

    const cutSteps = (debugTrace?.[cutClassId] ?? []).map(s => ({...s, _source: 'cut'}));
    const testSteps = (debugTrace?.[testClassId] ?? []).map(s => ({...s, _source: 'test'}));

    // Merge both sorted by global index
    const allSteps = [...cutSteps, ...testSteps].sort((a, b) => a.globalIndex - b.globalIndex);
    _debugSteps = allSteps.length > 0 ? allSteps : null;
    _debugStepIndex = 0;

    if (!_debugSteps) {
        _hideStepper();
        return;
    }

    // Like IntelliJ: jump straight to the first breakpoint, or to the end if none is set.
    _debugStepIndex = _firstBreakpointIndex();

    document.getElementById('bottom-panel').setAttribute('aria-hidden', 'false');
    switchTab('debugger');
    _renderCurrentStep();
}

// The line shown in its editor for a step. In the debug strand the runner body
// (right editor) is offset by the wrapper header the server prepends before running.
function _stepDisplayLine(step) {
    return _toDisplayLine(step.lineNumber, step._source === 'test' ? 'test' : 'debug');
}

function _toDisplayLine(lineNumber, editorKey) {
    return (isDebugStrand() && editorKey === 'test')
        ? lineNumber - _runnerLineOffset
        : lineNumber;
}

/** @return {number} index of the first step sitting on a breakpoint, or the last step if none. */
function _firstBreakpointIndex() {
    if (!_debugSteps) return 0;
    for (let i = 0; i < _debugSteps.length; i++) {
        const s = _debugSteps[i];
        const editorKey = s._source === 'test' ? 'test' : 'debug';
        if (_breakpoints[editorKey].has(_stepDisplayLine(s))) return i;
    }
    return _debugSteps.length - 1;
}

// Render one variable row, recursing into a collapsible tree for compound values
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

// Render the current step: label, nav buttons, variables and line highlight
function _renderCurrentStep() {
    if (!_debugSteps) return;
    const step = _debugSteps[_debugStepIndex];
    const isCut = step._source !== 'test';
    const badgeCls = isCut ? 'cut' : 'test';
    const badgeText = isCut ? 'Class' : (isDebugStrand() ? 'Runner' : 'Test');

    const labelEl = document.getElementById('debug-step-label');
    labelEl.innerHTML =
        `<span class="debug-source-badge ${badgeCls}">${_e(badgeText)}</span>` +
        `Step ${_debugStepIndex + 1}&thinsp;/&thinsp;${_debugSteps.length}` +
        `&ensp;&middot;&ensp;Line ${_stepDisplayLine(step)}` +
        `&ensp;&middot;&ensp;${_e(step.methodName)}`;

    const atEnd = _debugStepIndex === _debugSteps.length - 1;
    document.getElementById('debug-prev-btn').disabled = _debugStepIndex === 0;
    document.getElementById('debug-next-btn').disabled = atEnd;

    const continueBtn = document.getElementById('debug-continue-btn');
    continueBtn.disabled = false;
    continueBtn.classList.toggle('is-results', atEnd);
    continueBtn.innerHTML = atEnd ? 'Results &#9654;' : '&#9654;&#9654; Continue';
    continueBtn.title = atEnd ? 'Show test results' : 'Continue to next breakpoint [F8]';

    const container = document.getElementById('debug-vars-body');
    const entries = Object.entries(step.variables ?? {});
    if (entries.length === 0) {
        container.innerHTML = '<div class="no-vars">No variables in scope yet</div>';
    } else {
        container.innerHTML = entries.map(([k, v]) => _renderRow(k, v)).join('');
    }

    _highlightDebugLine(_stepDisplayLine(step), step._source);
    if (_debugRevealCoverage) _applyCoverageDecorations();
}

// Turn the current-line highlight on (once) when the user starts stepping
function _enableDebugHighlight() {
    if (!_debugHighlightOn) {
        _debugHighlightOn = true;
        document.getElementById('toggle-debug-btn').classList.add('is-on');
    }
}

function debugStepPrev(source = 'button') {
    if (_debugSteps && _debugStepIndex > 0) {
        _enableDebugHighlight();
        _debugStepIndex--;
        trackClientEvent('debug-step-prev', {step: _debugStepIndex + 1, source});
        _renderCurrentStep();
    }
}

function debugStepNext(source = 'button') {
    if (_debugSteps && _debugStepIndex < _debugSteps.length - 1) {
        _enableDebugHighlight();
        _debugStepIndex++;
        trackClientEvent('debug-step-next', {step: _debugStepIndex + 1, source});
        _renderCurrentStep();
    }
}

// Jump to the next breakpoint; at the end switch to the results tab
function debugContinue(source = 'button') {
    if (!_debugSteps) return;
    if (_debugStepIndex === _debugSteps.length - 1) {
        trackClientEvent('debug-continue', {reachedResults: true, source});
        switchTab('results');
        return;
    }
    trackClientEvent('debug-continue', {reachedResults: false, fromStep: _debugStepIndex + 1, source});
    _enableDebugHighlight();
    for (let i = _debugStepIndex + 1; i < _debugSteps.length; i++) {
        const s = _debugSteps[i];
        const editorKey = s._source === 'test' ? 'test' : 'debug';
        if (_breakpoints[editorKey].has(_stepDisplayLine(s))) {
            _debugStepIndex = i;
            _renderCurrentStep();
            return;
        }
    }
    // No breakpoint
    _debugStepIndex = _debugSteps.length - 1;
    _renderCurrentStep();
    switchTab('results');
}

const _currentLineDecors = {debug: [], test: []};

// Highlight the active line in the relevant editor and clear the other
function _highlightDebugLine(lineNumber, source) {
    const effectiveLine = (_debugHighlightOn && lineNumber > 0) ? lineNumber : null;
    ['debug', 'test'].forEach(key => {
        const editor = window.editors.monaco[key];
        if (!editor) return;
        const isActive = (key === 'test') === (source === 'test');
        const decos = (isActive && effectiveLine) ? [{
            range: new monaco.Range(lineNumber, 1, lineNumber, 1),
            options: {
                isWholeLine: true,
                className: 'debug-current-line',
                glyphMarginClassName: 'debug-current-line-glyph',
            }
        }] : [];
        _currentLineDecors[key] = editor.deltaDecorations(_currentLineDecors[key], decos);
    });
    if (lineNumber > 0) {
        const activeKey = source === 'test' ? 'test' : 'debug';
        window.editors.monaco[activeKey]?.revealLineInCenter(lineNumber);
    }
}

// Reset the stepper UI to its empty "no trace" state
function _hideStepper() {
    _debugRevealCoverage = false;
    _applyCoverageDecorations();
    _highlightDebugLine(null);
    document.getElementById('debug-prev-btn').disabled = true;
    document.getElementById('debug-next-btn').disabled = true;
    const continueBtn = document.getElementById('debug-continue-btn');
    continueBtn.disabled = true;
    continueBtn.classList.remove('is-results');
    continueBtn.innerHTML = '&#9654;&#9654; Continue';
    continueBtn.title = 'Continue to next breakpoint [F8]';
    document.getElementById('debug-step-label').textContent = 'Run tests to see debug trace';
    document.getElementById('debug-vars-body').innerHTML =
        '<div class="no-vars">No trace available yet</div>';
}

document.addEventListener('keydown', ev => {
    if (!_debugSteps) return;
    if (ev.target instanceof Element && ev.target.closest('.monaco-editor')) return;
    if (ev.key === 'ArrowLeft') {
        ev.preventDefault();
        debugStepPrev('keyboard');
    }
    if (ev.key === 'ArrowRight') {
        ev.preventDefault();
        debugStepNext('keyboard');
    }
    if (ev.key === 'ArrowDown') {
        ev.preventDefault();
        debugContinue('keyboard');
    }
});

let _coverageHighlightOn = true;

// Toggle the coverage line highlighting on/off
function toggleCoverageHighlight() {
    _coverageHighlightOn = !_coverageHighlightOn;
    document.getElementById('toggle-coverage-btn').classList.toggle('is-on', _coverageHighlightOn);
    trackClientEvent('debug-toggle-coverage', {on: _coverageHighlightOn});
    _applyCoverageDecorations();
}

let _debugHighlightOn = true;

// Toggle the current-line debug highlighting on/off
function toggleDebugHighlight() {
    _debugHighlightOn = !_debugHighlightOn;
    document.getElementById('toggle-debug-btn').classList.toggle('is-on', _debugHighlightOn);
    trackClientEvent('debug-toggle-highlight', {on: _debugHighlightOn});
    if (_debugHighlightOn && _debugSteps) _renderCurrentStep();
    else _highlightDebugLine(null);
}

// Switch the bottom-panel tab and reveal step-limited coverage only on the debugger tab
function switchTab(name) {
    ['debugger', 'results'].forEach(t => {
        document.getElementById(`tab-btn-${t}`).classList.toggle('active', t === name);
        document.getElementById(`tab-pane-${t}`).setAttribute('aria-hidden', t === name ? 'false' : 'true');
    });
    _debugRevealCoverage = name === 'debugger';
    _applyCoverageDecorations();
}


const [encodeHtmlEntities, decodeHtmlEntities] = (() => {
    const encoder = document.createElement('textarea');
    return [(html) => {
        encoder.innerText = html;
        return encoder.innerHTML;
    }, (text) => {
        encoder.innerHTML = text;
        return encoder.innerText;
    }];
})();
const [_e, _d] = [encodeHtmlEntities, decodeHtmlEntities]; // shorthand

/**
 * Show or hide the "Debugger" tab in the bottom panel. The tab 
 * only appears after an explicit Debug run.
 * @param {boolean} visible
 */
function setDebuggerTabVisible(visible) {
    const tabBtn = document.getElementById('tab-btn-debugger');
    tabBtn.style.display = visible ? '' : 'none';
    if (!visible && tabBtn.classList.contains('active')) {
        switchTab('results');
    }
}

/**
 * @param {TestResult} obj
 * @param {boolean} showDebugTrace
 */
function renderTestResultObject(obj, showDebugTrace = false) {
    _updateExecutableLines(obj.executableLines);
    renderCoverage(obj.coverage);
    renderLogs(obj.logs);

    let r = `<strong>${obj.testClassName} </strong>`;

    if (obj.testStatus === 'PASSED') {
        r += `<div class="clr-success">All tests passed.</div>`;
    } else if (obj.testStatus === 'FAILED') {
        r += `<div class="clr-error">There are test failures.</div>`;
    }

    r += '<ul class="info-list">';
    for (const [fn, details] of Object.entries(obj.testDetails)) {
        r += '<li>';
        r += `${details.className}::<strong>${fn} </strong>`;
        if (details.accessDenied != null) {
            r += `<span class="clr-error">Access Denied!<br>${details.accessDenied}</span><br>`;
        } else if (details.expectedTestResult != null || details.actualTestResult != null) {
            r += `
                <div class="clr-success flex"><p>Expected value:</p> <pre>${_e(details.expectedTestResult)}</pre></div>
                <div class="clr-error flex"><p>Actual value:</p> <pre>${_e(details.actualTestResult)}</pre></div>
            `;
        } else if (details.trace != null) {
            r += `<br><small class="clr-error">Trace: <pre>${_e(details.trace)}</pre></small>`;
        } else if (details.testStatus === "PASSED") {
            r += `<span class="clr-success">Passed!</span><br>`;
        } else if (details.testStatus === "IGNORED") {
            r += `<span class="clr-warning">Ignored</span><br>`;
        } else {
            r += `<span class="clr-error">Error</span><br>`;
        }

        let logs = obj.logs[window.cutClassName + '#' + window.userId] ?? [];
        logs = logs.filter(log => log.testMethodName === fn);
        if (logs.length < 1) {
            r += `<small>(No log messages)</small>`;
        } else {
            r += `<details><summary>Log messages</summary>`;
            r += `<ul>`;
            logs.sort((a, b) => a.orderIndex - b.orderIndex);
            for (const log of logs) {
                r += `<li><!--${log.orderIndex}--><span class="method-and-line">[${_e(log.methodName)}:${log.lineNumber}]</span> <strong><code>${_e(log.message)}</code></strong></li>`;
            }
            r += `</ul></details>`;
        }

        r += '</li>';
    }
    r += '</ul>';
    renderResult(r + `<br><small>Elapsed time: ${obj.elapsedTime} ms</small>`);

    // The debugger tab only shows up for an explicit Debug run.
    if (showDebugTrace) {
        setDebuggerTabVisible(true);
        renderDebugTrace(obj.debugTrace);
    } else {
        _hideStepper();
        setDebuggerTabVisible(false);
    }
}


window.editors.monaco.test.onDidChangeModelContent(onContentChangedTests);
window.editors.monaco.debug.onDidChangeModelContent(onContentChangedCut);

function onContentChangedTests() {
    // disable activate button, because the tests need to be executed again
    disableActivateButton();
}

function onContentChangedCut() {
    // reset button might be useful now
    updateResetButtonState(currentComponent);
}

function closeEditor(source = 'button') {
    trackDebugEvent('debug-editor-closed', {source});
    if (currentComponent) save(currentComponent); // auto save on close
    if (isDebugStrand()) window.objectiveDisplay?.setInteractionOpen(false);
    currentComponent = false;
    uiOverlay.setAttribute('aria-hidden', 'true');
    document.getElementById('unity-canvas').focus();
    window.unityInstance.SendMessage('BrowserInterface', 'OnEditorClose');
    _hideStepper();
}

// wrapped so the click Event isn't forwarded as the `source` argument
document.getElementById('editor-close-btn').addEventListener('click', () => closeEditor());

const execBtn = document.getElementById('editor-execute-btn');
const debugBtn = document.getElementById('editor-debug-btn');

function setExecuteDisabled(disabled) {
    execBtn.disabled = disabled;
    debugBtn.disabled = disabled;
}

const execute = async (debug = false) => {
    const componentName = currentComponent;
    if (!componentName) {
        renderResult(`<p class="clr-error">There is no component loaded currently.</p>`);
        return;
    }
    
    if (isDebugStrand()) {
        return executeRunner(componentName, debug);
    }

    const code = window.editors.monaco.test.getValue();
    renderResult(debug ? '<p>Debugging test...</p>' : '<p>Executing test...</p>');
    setExecuteDisabled(true);

    if (gameProgress?.status === 'DEBUGGING') {
        await save(componentName); // save CUT
    }

    return fetch(`${apiUrl}/components/${componentName}/test/execute`, {
        method: 'POST',
        headers: jsonHeader,
        body: JSON.stringify({code}),
    }).then(res => {
        if (res.status === 401) {
            renderResult(`<p class="clr-error">Your session has expired. <a href="/login">Login again.</a></p>`);
            return;
        }

        res.json().then(/** @param {TestResult} obj */obj => {
            console.log(obj);
            setExecuteDisabled(false);

            if (!res.ok) {
                renderResult(`
                        <p class="clr-error"><strong>Failed to execute tests.</strong></p>
                        <pre class="clr-error">${obj.message}</pre>
                    `);
                return;
            }

            const data = componentData.get(componentName);
            if (data) { // should always be true, as the component is loaded before executing
                data.testResult = obj;
                componentData.set(componentName, data);
            }

            const className = window.cutClassName + '#' + window.userId;
            const lineCoverageNumbers = {
                covered: obj.coveredLines[className] ?? 0,
                total: obj.totalLines[className] ?? 0,
                percentage: obj.totalLines[className] === 0 ? 0 : Math.round(obj.coveredLines[className] / obj.totalLines[className] * 100),
                minimum: minLineCoverage,
            }

            renderTestResultObject(obj, debug);
            renderCoveragePercentage(lineCoverageNumbers);
            updateActivateButtonState(data);

            const isInTestState = gameProgress.status === 'TEST' && gameProgress.componentName === componentName;
            const canNearlyActivate = isInTestState && data.testResult?.testStatus === 'PASSED';
            if (canNearlyActivate) {
                if (lineCoverageNumbers.percentage >= lineCoverageNumbers.minimum && !data.activationPopupShown) {
                    Popup.instance.open('can activate tests', lineCoverageNumbers)
                        .addButton('Activate', () => {
                            activateTests(componentName);
                        }, ['clr-success']);
                    data.activationPopupShown = true;
                } else {
                    if (!data.nearlyActivatePopupShown) {
                        Popup.instance.open('can nearly activate tests', lineCoverageNumbers);
                    }
                }
                data.nearlyActivatePopupShown = true;
                componentData.set(componentName, data);
            }
        })
    })
        .catch(e => {
            console.error(e);
            setExecuteDisabled(false);
            renderResult(`<p class="clr-error"><strong>Failed to execute test due to network issues.</strong></p>`);
        });
}
execBtn.addEventListener('click', () => execute(false));
debugBtn.addEventListener('click', () => execute(true));

async function executeRunner(componentName, debug = false) {
    trackClientEvent(debug ? 'debug-debug-clicked' : 'debug-run-clicked');
    renderResult(debug ? '<p>Debugging...</p>' : '<p>Running...</p>');
    setExecuteDisabled(true);

    await save(componentName, true); // persists the CUT edits; the runner is saved by /debug/execute itself

    const code = window.editors.monaco.test.getValue();

    try {
        const res = await fetch(`${apiUrl}/components/${componentName}/debug/execute`, {
            method: 'POST',
            headers: jsonHeader,
            body: JSON.stringify({code}),
        });
        setExecuteDisabled(false);

        if (res.status === 401) {
            renderResult(`<p class="clr-error">Your session has expired. <a href="/login">Login again.</a></p>`);
            return;
        }
        if (!res.ok) {
            const err = await res.json().catch(() => ({message: 'Unknown error'}));
            renderResult(`<p class="clr-error"><strong>Execution failed.</strong></p>
                          <pre class="clr-error">${_e(err.message ?? '')}</pre>`);
            return;
        }

        const obj = await res.json();
        console.log('Debug execution result:', obj);
        renderDebugRunResult(obj, debug);
    } catch (e) {
        console.error(e);
        setExecuteDisabled(false);
        renderResult(`<p class="clr-error"><strong>Failed to run due to network issues.</strong></p>`);
    }
}

function renderDebugRunResult(obj, showDebug = false) {
    _runnerLineOffset = obj.runnerLineOffset ?? 0;
    _updateExecutableLines(obj.executableLines);
    renderLogs(obj.logs);

    let r = obj.hiddenTestsPassed
        ? '<p class="clr-success">Hidden tests passed ✓</p>'
        : '<p class="clr-error">Hidden tests failed ✗</p>';
    if (obj.hiddenTestsError) {
        r += `<pre class="clr-error">${_e(obj.hiddenTestsError)}</pre>`;
    }

    if (obj.testStatus !== 'PASSED') {
        for (const details of Object.values(obj.testDetails ?? {})) {
            if (details.trace) {
                r += `<span class="clr-error">Exception:</span><pre class="clr-error">${_e(details.trace)}</pre>`;
            } else if (details.accessDenied) {
                r += `<span class="clr-error">Access denied: ${_e(details.accessDenied)}</span>`;
            } else if (details.testStatus !== 'PASSED') {
                r += `<span class="clr-error">Run failed (${_e(details.testStatus)})</span>`;
            }
        }
    }
    renderResult(r);
    if (showDebug) {
        setDebuggerTabVisible(true);
        renderDebugTrace(obj.debugTrace);
    } else {
        _hideStepper();
        setDebuggerTabVisible(false);
    }
}

function updateResetButtonState(componentName) {
    const resetCutButton = document.getElementById('editor-reset-cut-btn');
    resetCutButton.style.display = gameProgress?.status === 'DEBUGGING' ? 'block' : 'none';
    resetCutButton.disabled = gameProgress?.status !== 'DEBUGGING';
}

async function resetCut() {
    const componentName = currentComponent;
    if (!componentName) return;
    const currentComponentData = await getComponentData(componentName, true, !isDebugStrand());
    const resetCutButton = document.getElementById('editor-reset-cut-btn');

    // The popup's default CTA ("Keep my changes") is the cancel path; track it unless
    // the Reset button below fires first and cancels this listener.
    const continueButton = document.getElementById('continue-button');
    const trackCancelled = () => trackDebugEvent('debug-reset-cut-clicked', {confirmed: false});
    continueButton.addEventListener('click', trackCancelled, {once: true});

    Popup.instance.open('reset cut').addButton('Reset', () => {
        continueButton.removeEventListener('click', trackCancelled);
        trackDebugEvent('debug-reset-cut-clicked', {confirmed: true});
        Popup.instance.open('wait', {'for': 'Resetting the class under test'});
        fetch(`${apiUrl}/components/${componentName}/cut/reset`, {headers: jsonHeader, method: 'POST'})
            .then(res => {
                if (res.ok) {
                    res.json().then(/** @param {SourceDTO} json */json => {
                        currentComponentData.cut = json;
                        componentData.set(componentName, currentComponentData);
                        window.editors.monaco.debug.setValue(json.sourceCode);
                        Popup.instance.close();
                        resetCutButton.style.display = 'none';
                    });
                } else {
                    Popup.instance.open('error');
                }
            });
    }, ['clr-error']);
}

document.getElementById('editor-reset-cut-btn').addEventListener('click', resetCut);

/**
 * @param {string} componentName
 * @param {boolean} useCache
 * @param {boolean} includeTest Whether to fetch the test as well
 * @return {Promise<ComponentData>}
 */
async function getComponentData(componentName, useCache = true, includeTest = true) {
    let data = {};
    const onError = res => {
        Popup.instance.open('error').onClose(closeEditor);
        console.error(res);
    }

    if (componentData.has(componentName)) {
        data = componentData.get(componentName);
    }

    if (!data.cut || !useCache) {
        await fetch(`${apiUrl}/components/${componentName}/cut/src`, {headers: authHeader}).then(res => {
            if (!res.ok) {
                onError(res);
            } else {
                return res.json().then(/** @param {SourceDTO} json */json => {
                    data.cut = json;
                });
            }
        }).catch(onError);
    }

    if (includeTest && (!data.test || !useCache)) {
        await fetch(`${apiUrl}/components/${componentName}/test/src`, {headers: authHeader}).then(res => {
            if (!res.ok) {
                onError(res);
            } else {
                return res.json().then(/** @param {SourceDTO} test */test => {
                    data.test = test;
                });
            }
        }).catch(onError);
    }

    componentData.set(componentName, data);
    return data;
}

function updateActivateButtonState(data) {
    const btn = document.getElementById('editor-activate-test-btn');
    const className = window.cutClassName + '#' + window.userId;

    btn.style.display = gameProgress?.status === 'TEST' ? 'block' : 'none';

    // can be activated if not already active and tests passed under the original CUT
    const isActivated = gameProgress?.status === 'TESTS_ACTIVE';
    const testsPassed = data.testResult?.testStatus === 'PASSED';
    const enoughCoverage = data.testResult && data.testResult.coveredLines[className] / data.testResult.totalLines[className] * 100 >= minLineCoverage;
    const canActivate = !isActivated && testsPassed && enoughCoverage;

    btn.disabled = !canActivate;
    btn.querySelector('.text').innerText =
        canActivate ? "Activate Test" :
            isActivated ? "Test Activated" :
                !testsPassed ? "tests need to pass to activate" :
                    !enoughCoverage ? "get higher line coverage to activate" : "// unreachable";
    btn.querySelector('.activate-icon').classList.toggle('hidden', !canActivate && !isActivated);
    btn.querySelector('.coverage-icon').classList.toggle('hidden', enoughCoverage || !testsPassed);
    btn.querySelector('.error-icon').classList.toggle('hidden', testsPassed);
}

function disableActivateButton() {
    const btn = document.getElementById('editor-activate-test-btn');
    btn.disabled = true;
    btn.querySelector('.text').innerText = "tests need to pass to activate";
    btn.querySelector('.activate-icon').classList.toggle('hidden', true);
    btn.querySelector('.coverage-icon').classList.toggle('hidden', true);
    btn.querySelector('.error-icon').classList.toggle('hidden', false);
}

async function activateTests() {
    const componentName = currentComponent;
    if (!componentName) return;

    const event = new ComponentTestsActivatedEvent(componentName);
    window.es.sendEvent(event);
    const data = await getComponentData(componentName);

    setExecuteDisabled(true);
    updateActivateButtonState(data);
    renderResult(`<p>Test activated for ${componentName}.</p>`);
    constrain([], 'test');

    Popup.instance.open('tests activated').onClose(closeEditor);
}

document.getElementById('editor-activate-test-btn').addEventListener('click', activateTests);


// Unity calls this (via the jslib) to enter the debug strand; reuses the normal editor opener
window.openDebugger = function (componentName) {
    return window.openEditor(componentName);
};

window.setPuzzleOpen = function (open) {
    try {
        window.objectiveDisplay?.setInteractionOpen(!!open);
    } catch (e) {
        console.error('setPuzzleOpen failed', e);
    }
};

window.openEditor = async function (componentName) {
    // Check if the introduction should be shown. Then show it immediately, so the user can read it while the editor is still loading.
    // Debug strand has its own per-room intro popup (see below) and never shows this testing-strand one.
    if (!isDebugStrand()) {
        Settings.instance.get(Settings.keys.codeEditorIntroductionShown).then(introductionShown => {
            if (!introductionShown) {
                Popup.instance.open('code editor introduction').onClose(() => {
                    Settings.instance.set(Settings.keys.codeEditorIntroductionShown, true);
                });
            }
        });
    }

    const activateButton = document.getElementById('editor-activate-test-btn');
    const resetCutButton = document.getElementById('editor-reset-cut-btn');
    setExecuteDisabled(true);
    activateButton.disabled = true;
    resetCutButton.disabled = true;
    constrain([], 'debug');
    constrain([], 'test');
    
    const debugging = gameProgress?.mode === 'Debugging';
    uiOverlay.classList.toggle('mode-debugging', debugging);
    uiOverlay.classList.toggle('mode-testing', !debugging);

    setDebuggerTabVisible(false); // hide debugger tab until an explicit Debug run
    renderResult('');
    renderCoveragePercentage(null);
    currentComponent = componentName;
    window.editors.monaco.debug.setValue(loadingText);
    window.editors.monaco.test.setValue(loadingText);
    window.editors.monaco.debug.layout();
    window.editors.monaco.test.layout();
    uiOverlay.setAttribute('aria-hidden', 'false');

    // Start the runner fetch in parallel with the CUT fetch (debug strand only)
    const runnerPromise = debugging ? loadRunner(componentName) : null;
    // do not use cache here to get a fresh set of editableRanges; the debug strand has no test source
    const currentComponentData = await getComponentData(componentName, false, !debugging);

    window.editors.monaco.debug.setValue(currentComponentData.cut.sourceCode);
    const isMutated = debugging || ['MUTATED', 'DESTROYED', 'DEBUGGING'].includes(gameProgress?.status);
    // make it not editable if not attacked/mutated.
    constrain(isMutated ? currentComponentData.cut.editable : [], 'debug');
    monacoContainerDebug.classList.toggle('mutated', isMutated);
    monacoContainerTest.classList.toggle('highlight', !isMutated);
    window.cutClassName = currentComponentData.cut.className;

    if (debugging) {
        // Right editor is the free-form runner that drives the class under debug.
        const runnerCode = (await runnerPromise) ?? makeRunnerTemplate(currentComponentData.cut.className);
        window.editors.monaco.test.setValue(runnerCode);
        window.testClassName = 'DebugRunner';
        // fully editable: drop any leftover constraints from a prior testing session
        const testModel = window.editors.monaco.test.getModel();
        if (testModel._isRestrictedModel) {
            window.editors.restricted.test?.removeRestrictionsIn(testModel);
        }
    } else {
        window.editors.monaco.test.setValue(currentComponentData.test.sourceCode);
        constrain(currentComponentData.test.editable, 'test');
        window.testClassName = currentComponentData.test.className;
    }

    if (currentComponentData.testResult) {
        renderTestResultObject(currentComponentData.testResult);
    }

    updateActivateButtonState(currentComponentData);
    updateResetButtonState(componentName);
    if (gameProgress?.status !== 'TESTS_ACTIVE') {
        setExecuteDisabled(false);
    }

    if (gameProgress?.status === 'MUTATED' || gameProgress?.status === 'DESTROYED') {
        Popup.instance.open('start debugging').onClose(() => {
            es.sendEvent(new DebugStartEvent(componentName));
        });
    }
    
    if (debugging && gameProgress?.status === 'DEBUGGING') {
        window.objectiveDisplay?.setInteractionOpen(true);
        const shownCsv = (await Settings.instance.get(Settings.keys.debugRoomIntrosShown)) ?? '';
        const shown = shownCsv.split(',').filter(Boolean).map(Number);
        if (!shown.includes(gameProgress.room)) {
            Popup.instance.openDebugRoomIntro(gameProgress.room);
            await Settings.instance.set(Settings.keys.debugRoomIntrosShown, [...shown, gameProgress.room].join(','));
        }
    }

    window.editors.monaco.debug.layout();
    window.editors.monaco.test.layout();
    window.editors.monaco.debug.setScrollPosition({scrollTop: 0});
    window.editors.monaco.test.setScrollPosition({scrollTop: 0});
};


window.es = new EventSystem();
es.registerHandler('*', console.log);
es.registerHandler(
    'MutatedComponentTestsFailedEvent',
    /** @param {{executionResult:TestResult, cutSource:SourceDTO, testSource:SourceDTO, componentName:string}} evt */
    evt => {
        window.unityInstance.SendMessage('BrowserInterface', 'OnMutatedComponentTestsFailed', evt.componentName);
        /** @type {ComponentData} */
        const data = {
            testResult: evt.executionResult,
            cut: evt.cutSource,
            test: evt.testSource,
        };
        componentData.set(evt.componentName, data);
    }
);
es.registerHandler(
    'ComponentDestroyedEvent',
    /** @param {{executionResult:TestResult, cutSource:SourceDTO, componentName:string, autoGeneratedTestSource:SourceDTO}} evt */
    evt => {
        window.unityInstance.SendMessage('BrowserInterface', 'OnComponentDestroyed', evt.componentName);
        const data = {
            testResult: evt.executionResult,
            cut: evt.cutSource,
            test: evt.autoGeneratedTestSource,
        };
        componentData.set(evt.componentName, data);
    }
);
es.registerHandler(
  'ComponentTestsExtendedEvent',
  /** @param {{componentName:string, addedTestMethodName:string}} evt */
  evt => {
      fetch(`${apiUrl}/components/${evt.componentName}/test/src`, {headers: authHeader})
        .then(res => res.json())
        .then(/** @param {SourceDTO} test */ async test => {
            const data = await getComponentData(evt.componentName);
            data.test = test;
            componentData.set(evt.componentName, data);
            console.log('Test for ' + evt.componentName + ' extended with ' + evt.addedTestMethodName);
            // Is also fired when destroyed now, so only show the popup if it's happening during debugging
            if (gameProgress.status === "DEBUGGING") {
                Popup.instance.open('test extended', evt);
            }
            
                if (!isDebugStrand() && currentComponent === evt.componentName) {
                    window.editors.monaco.test.setValue(test.sourceCode);
                    constrain(test.editable, 'test');
                }
            });
    }
);
es.registerHandler(
    GameProgressionChangedEvent.type,
    /** @param {{progression:UserGameProgressionDTO}} evt */
    evt => {
        gameProgress = evt.progression;
        window.unityInstance.SendMessage('BrowserInterface', 'OnGameProgressionChanged', JSON.stringify(evt.progression));

        console.log('Game progression changed: ', evt.progression);
    }
);
es.registerHandler(
    'ComponentFixedEvent',
    /** @param {{componentName:string}} evt */
    async evt => {
        const data = await getComponentData(evt.componentName);
        componentData.set(evt.componentName, data);
        window.unityInstance.SendMessage('BrowserInterface', 'OnComponentFixed', evt.componentName);
        const popup = isDebugStrand()
            ? Popup.instance.openDebugComponentFixed(evt.componentName)
            : Popup.instance.open('component fixed');
        popup.onTransitionEnd(closeEditor);
    }
);
es.registerHandler(
    GameStartedEvent.type,
    () => void Popup.instance.open('welcome').onClose(() => {
        document.getElementById('unity-canvas').focus();
    })
);
// Set when the server reports the game finished. In the debug strand the popup is held back
// until Unity has played its epilogue dialogue (window.onEpilogueFinished below), otherwise the
// 'component fixed' popup (opened async, after getComponentData) would overwrite it right away.
let gameFinished = false;

function endSession() {
    const popup = Popup.instance.open(isDebugStrand() ? 'game finished debug' : 'game finished');
    popup.onTransitionEnd(() => window.location.replace('/'));
}

es.registerHandler(
    'GameFinishedEvent',
    () => {
        gameFinished = true;
        if (!isDebugStrand()) endSession();
    }
);

// Unity calls this (via the jslib) once the final room's epilogue dialogue is over.
window.onEpilogueFinished = function () {
    if (!gameFinished) return;
    gameFinished = false;
    endSession();
};

document.addEventListener('keydown', e => {
    const ctrlOrCmd = e.ctrlKey || e.metaKey;
    const editorOpen = uiOverlay.getAttribute('aria-hidden') === 'false' && currentComponent;
    if (ctrlOrCmd && e.key === 's') {
        e.preventDefault();
        if (editorOpen) {
            trackDebugEvent('debug-save-shortcut', {source: 'keyboard'});
            save(currentComponent);
        } else {
            console.log('Editor closed, not saving.');
        }
    }
    if (e.key === 'F10') {
        e.preventDefault();
        if (uiOverlay.getAttribute('aria-hidden') === 'false' && currentComponent) {
            execute();
        }
    }
    if (e.key === 'Escape') {
        if (editorOpen) {
            e.preventDefault();
            closeEditor('keyboard');
        }
    }
});

window.objectiveDisplay = new ObjectiveDisplay('#objective');
window.surveyDisplay = new OffViewportInfo('#survey');
