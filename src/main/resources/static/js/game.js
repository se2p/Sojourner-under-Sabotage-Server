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
window.editors = {monaco:{}, restricted:{}};
const monacoContainerDebug = document.getElementById('monaco-container-debug');
window.editors.monaco.debug = monaco.editor.create(monacoContainerDebug, {
    value: '',
    language: 'java',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 16,
});

const monacoContainerTest = document.getElementById('monaco-container-test');
window.editors.monaco.test = monaco.editor.create(monacoContainerTest, {
    value: '',
    language: 'java',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 16,
});

// top right buttons: add confirm dialogue to anchors
document.getElementById('reset-game-button').addEventListener('click', ev => {
    ev.preventDefault();
    Popup.instance.open('reset').addButton(
            'Reset',
        () => window.location.replace('/reset'),
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
    const editorContainers = document.querySelectorAll('.monaco-editor-container');
    editorContainers.forEach(el => el.style.height = '0');
    editors.monaco.test.layout();
    editors.monaco.debug.layout();
    editorContainers.forEach(el => el.style.height = 'initial');
}

function sessionExpired(statusInfo) {
    renderResult(`<p class="clr-error">Your session has expired.
                <a href="/login" target="_blank" rel="noopener">Login again.</a></p>`);
    statusInfo.innerText = "Save Failed";
}

async function save(componentName = currentComponent) {
    if (!currentComponent || window.editors.monaco.test.getValue() === loadingText) {
        console.log('No component loaded, not saving')
        return;
    }

    let noSaveFailure = true;
    const statusInfo = document.getElementById('editor-status-text');
    statusInfo.innerText = "Saving...";
    const data = componentData.get(componentName);

    // 1 ─ Save test
    if (gameProgress?.status === 'TEST' || gameProgress?.status === 'DEBUGGING') {
        const test = window.editors.monaco.test.getValue();
        await fetch(`/api/components/${componentName}/test/src`, {
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

    // 2 ─ Save cut (only in debug mode, after a component was mutated)
    if (gameProgress?.status === 'DEBUGGING') {
        const cut = window.editors.monaco.debug.getValue();
        await fetch(`/api/components/${componentName}/cut/src`, {
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
        constrainedInstance.removeRestrictionsIn(editor.getModel());
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

function renderCoverage(coverage) {
    const model = window.editors.monaco.debug.getModel();
    const cutClassId = window.cutClassName + '#' + window.userId;
    const linesVisited = Object.entries(coverage?.[cutClassId] ?? {});
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
 * @param {Object<string, Object<number, Object<string, string>>>} variables
 */
function renderDebugValues(variables) {
    const cutClassId = window.cutClassName + '#' + window.userId;
    const data = variables?.[cutClassId] ?? {};
    const hints = [];
    for (const [line, vars] of Object.entries(data)) {
        let sep = '//';
        for (const [varName, value] of Object.entries(vars)) {
            const shortName = varName.split('/').pop();
            hints.push({
                kind: monaco.languages.InlayHintKind.Type,
                position: { column: Number.MAX_VALUE, lineNumber: parseInt(line) },
                label: `${sep} ${shortName} = ${value}`,
                paddingLeft: true,
                tooltip: `The variable ${shortName} is assigned to the value "${value}" here.`,
            });
            sep = ',';
        }
    }

    if (window.disposeHints) window.disposeHints.dispose();
    window.disposeHints = monaco.languages.registerInlayHintsProvider("java", {
        provideInlayHints(model, range, token) {
            const dispose = () => {};
            if (model === window.editors.monaco.debug.getModel()) {
                return {hints, dispose};
            } else {
                return {hints: [], dispose};
            }
        },
    });
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

/** @param {TestResult} obj */
function renderTestResultObject(obj) {
    renderCoverage(obj.coverage);
    // renderDebugValues(obj.variables); // not very useful without breakpoints
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
            r +=`<span class="clr-error">Access Denied!<br>${details.accessDenied}</span><br>`;
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
}


window.editors.monaco.test.onDidChangeModelContent(onContentChangedTests);
window.editors.monaco.debug.onDidChangeModelContent(onContentChangedCut);
function onContentChangedTests() {
    // hide variable value hints, as they can get confusing while editing
    if (window.disposeHints) window.disposeHints.dispose();

    // disable activate button, because the tests need to be executed again
    disableActivateButton();
}
function onContentChangedCut() {
    // hide variable value hints, as they can get confusing while editing
    if (window.disposeHints) window.disposeHints.dispose();

    // reset button might be useful now
    updateResetButtonState(currentComponent);
}

function closeEditor() {
    if (currentComponent) save(currentComponent); // auto save on close
    currentComponent = false;
    uiOverlay.setAttribute('aria-hidden', 'true');
    document.getElementById('unity-canvas').focus();
    window.unityInstance.SendMessage('BrowserInterface', 'OnEditorClose');
}
document.getElementById('editor-close-btn').addEventListener('click', closeEditor);

const execBtn = document.getElementById('editor-execute-btn');
const execute = async () => {
    const componentName = currentComponent;
    if (!componentName) {
        renderResult(`<p class="clr-error">There is no component loaded currently.</p>`);
        return;
    }
    const code = window.editors.monaco.test.getValue();
    renderResult('<p>Executing test...</p>');
    execBtn.disabled = true;

    if (gameProgress?.status === 'DEBUGGING') {
        await save(componentName); // save CUT
    }

    return fetch(`/api/components/${componentName}/test/execute`, {
        method: 'POST',
        headers: jsonHeader,
        body: JSON.stringify({code}),
    }).then(res => {
        if (res.status === 401) {
            renderResult(`<p class="clr-error">Your session has expired. <a href="/login">Login again.</a></p>`);
            return;
        }

        res.json().then(/** @param {TestResult} obj */ obj => {
            console.log(obj);
            execBtn.disabled = false;

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

            renderTestResultObject(obj);
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
        execBtn.disabled = false;
        renderResult(`<p class="clr-error"><strong>Failed to execute test due to network issues.</strong></p>`);
    });
}
execBtn.addEventListener('click', execute);

function updateResetButtonState(componentName) {
    const resetCutButton = document.getElementById('editor-reset-cut-btn');
    resetCutButton.style.display = gameProgress?.status === 'DEBUGGING' ? 'block' : 'none';
    resetCutButton.disabled = gameProgress?.status !== 'DEBUGGING';
}
async function resetCut() {
    const componentName = currentComponent;
    if (!componentName) return;
    const currentComponentData = await getComponentData(componentName);
    const resetCutButton = document.getElementById('editor-reset-cut-btn');

    Popup.instance.open('reset cut').addButton('Reset', () => {
        Popup.instance.open('wait', {'for': 'Resetting the class under test'});
        fetch(`/api/components/${componentName}/cut/reset`, {headers: jsonHeader, method: 'POST'})
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
 * @return {Promise<ComponentData>}
 */
async function getComponentData(componentName, useCache = true) {
    let data = {};
    const onError = res => {
        Popup.instance.open('error').onClose(closeEditor);
        console.error(res);
    }

    if (componentData.has(componentName)) {
        data = componentData.get(componentName);
    }

    if (!data.cut || !useCache) {
        await fetch(`/api/components/${componentName}/cut/src`, {headers: authHeader}).then(res => {
            if (!res.ok) {
                onError(res);
            } else {
                return res.json().then(/** @param {SourceDTO} json */json => {
                    data.cut = json;
                });
            }
        }).catch(onError);
    }

    if (!data.test || !useCache) {
        await fetch(`/api/components/${componentName}/test/src`, {headers: authHeader}).then(res => {
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

    execBtn.disabled = true;
    updateActivateButtonState(data);
    renderResult(`<p>Test activated for ${componentName}.</p>`);
    constrain([], 'test');

    Popup.instance.open('tests activated').onClose(closeEditor);
}
document.getElementById('editor-activate-test-btn').addEventListener('click', activateTests);


window.openEditor = async function (componentName) {
    // Check if the introduction should be shown. Then show it immediately, so the user can read it while the editor is still loading.
    Settings.instance.get(Settings.keys.codeEditorIntroductionShown).then(introductionShown => {
        if (!introductionShown) {
            Popup.instance.open('code editor introduction').onClose(() => {
                Settings.instance.set(Settings.keys.codeEditorIntroductionShown, true);
            });
        }
    });

    const activateButton = document.getElementById('editor-activate-test-btn');
    const resetCutButton = document.getElementById('editor-reset-cut-btn');
    execBtn.disabled = true;
    activateButton.disabled = true;
    resetCutButton.disabled = true;
    constrain([], 'debug');
    constrain([], 'test');

    renderResult('');
    renderCoveragePercentage(null);
    currentComponent = componentName;
    window.editors.monaco.debug.setValue(loadingText);
    window.editors.monaco.test.setValue(loadingText);
    window.editors.monaco.debug.layout();
    window.editors.monaco.test.layout();
    uiOverlay.setAttribute('aria-hidden', 'false');

    // do not use cache here to get a fresh set of editableRanges
    const currentComponentData = await getComponentData(componentName, false);

    window.editors.monaco.debug.setValue(currentComponentData.cut.sourceCode);
    const isMutated = ['MUTATED', 'DESTROYED', 'DEBUGGING'].includes(gameProgress?.status);
    // make it not editable if not attacked/mutated.
    constrain(isMutated ? currentComponentData.cut.editable : [], 'debug');
    monacoContainerDebug.classList.toggle('mutated', isMutated);
    monacoContainerTest.classList.toggle('highlight', !isMutated);
    window.cutClassName = currentComponentData.cut.className;

    window.editors.monaco.test.setValue(currentComponentData.test.sourceCode);
    constrain(currentComponentData.test.editable, 'test');
    window.testClassName = currentComponentData.test.className;

    if (currentComponentData.testResult) {
        renderTestResultObject(currentComponentData.testResult);
    }

    updateActivateButtonState(currentComponentData);
    updateResetButtonState(componentName);
    if (gameProgress?.status !== 'TESTS_ACTIVE') {
        execBtn.disabled = false;
    }

    if (gameProgress?.status === 'MUTATED' || gameProgress?.status === 'DESTROYED') {
        Popup.instance.open('start debugging').onClose(() => {
            es.sendEvent(new DebugStartEvent(componentName));
        });
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
      fetch(`/api/components/${evt.componentName}/test/src`, {headers: authHeader})
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

            if (currentComponent === evt.componentName) {
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
      Popup.instance.open('component fixed').onTransitionEnd(closeEditor);
  }
);
es.registerHandler(
    GameStartedEvent.type,
    () => void Popup.instance.open('welcome').onClose(() => {
        document.getElementById('unity-canvas').focus();
    })
);
es.registerHandler(
    'GameFinishedEvent',
    () => void Popup.instance.open('game finished')
);

document.addEventListener('keydown', e => {
    const ctrlOrCmd = e.ctrlKey || e.metaKey;
    const editorOpen = uiOverlay.getAttribute('aria-hidden') === 'false' && currentComponent;
    if (ctrlOrCmd && e.key === 's') {
        e.preventDefault();
        if (editorOpen) {
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
            closeEditor();
        }
    }
});

window.objectiveDisplay = new ObjectiveDisplay('#objective');
window.surveyDisplay = new OffViewportInfo('#survey');

document.getElementById("open-survey").addEventListener('click', (e) => {
    e.target.innerText = (e.target.innerText === "Pre-Questionnaire") ? "Post-Questionnaire" : "Feedback-Survey";
});
