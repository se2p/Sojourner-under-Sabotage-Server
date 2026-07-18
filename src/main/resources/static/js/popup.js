class Popup {

    /** @typedef {{title:string, content:string, cta:string, [btnClass]:string}} PopupText */
    /** @type {Map<string, PopupText|array<PopupText>>} */
    static #text = new Map([
        ['welcome', {
            title: 'Welcome to Sojourner under Sabotage!',
            content: `<p>You're on board the spaceship <i>Sojourner</i>...</p>`,
            cta: 'Begin the journey',
        }],
        ['start debugging', {
            title: 'The Component was Attacked!',
            content: `<p>The component has been mutated. You need to find the bug and fix it.</p>`,
            cta: 'Start Debugging',
        }],
        ['code editor introduction', [
            {
                title: 'The Code Editor',
                content: `<p><strong>Welcome to the code editor.</strong></p>
                          <p>It might look familiar to you as it's based on Visual Studio Code. You can use the 
                          same keyboard shortcuts and commands that you're used to from VSC.</p>`,
                cta: 'Next',
            },
            {
                title: 'But wait.. there are 2 editors?',
                content: `<p>The editor on the left contains the <strong>class under test (CUT)</strong>.<br>
                          Here you can read the code of the component you're currently inspecting.</p>
                          <p>The editor on the right is your <strong>test class</strong>.<br>
                          Here you can write your unit tests for the CUT using JUnit.</p>`,
                cta: 'Next',
            },
            {
                title: 'Running your tests',
                content: `<p>To try out your tests, hit the <strong>Run</strong>-button. <br>
                          It will execute the tests and show you the results at the bottom of the window.</p>`,
                cta: `Let's start!`,
            },
        ]],
        ['component fixed', {
            title: 'Great job!',
            content: `<p>You've fixed the bug and saved the component from failure.</p>
                      <p>The component is now up and running again!</p>`,
            cta: 'Continue',
        }],
        ['tests activated', {
            title: 'Tests activated',
            content: `<p>Nice! Now the tests are active and will detect if something is wrong with the component.</p>`,
            cta: 'Close component',
        }],
        ['can activate tests', {
            title: 'Your reached the minimum of {minimum} % coverage!',
            content: `<p>The tests for the component are passing and your coverage of <strong>{percentage} %</strong> 
                        is high enough now.<br>
                        If you think they're finished, you can activate the tests now!</p>
                     <p>Once activated, they cannot be changed again.</p>`,
            cta: 'Continue writing tests',
        }],
        ['can nearly activate tests', {
            title: 'The tests passed!',
            content: `<p>Awesome! The tests for the component are passing.</p>
                      <p>So far you only covered {covered} of {total} lines though. (That's {percentage} %)<br>
                        To ensure that your tests can catch as many potential bugs as possible, you should aim for a
                        coverage of at least {minimum} %.</p>`,
            cta: 'Continue writing tests',
        }],
        ['error', {
            title: 'An Error occurred',
            content: `<p>Something went wrong. Please try again.</p>`,
            cta: 'Close', btnClass: 'clr-error',
        }],
        ['test extended', {
            title: 'Something isn\'t quite right yet',
            content: `<p>While all your tests pass, something still seems off.</p>
                      <p>I wrote an additional test for you that may help you. You should be able to find it under the 
                      name "{addedTestMethodName}" below your own tests.</p>`,
            cta: 'Continue Debugging',
        }],
        ['logout', {
            title: 'Confirm Logout',
            content: `<p>Are you sure you want to close the game and log out?</p>`,
            cta: 'Continue Playing'
        }],
        ['reset', {
            title: 'Confirm Reset',
            content: `<p>Are you sure you want to reset your game?
                      <br>You'll lose your progress and start new in the first room.</p>`,
            cta: 'Continue Playing'
        }],
        ['game finished', {
            title: 'Congratulations!',
            content: `<p>You've successfully completed the game!</p>
                      <p>Thanks to you, the spaceship Sojourner can continue its mission. </p>`,
            cta: 'Back to menu'
        }],
        ['game finished debug', {
            title: 'Journey\'s End... For Now',
            content: `<p>This planet's civilization is beyond saving. Whatever crisis they faced, they brought it on
                      themselves, and by the time you traced the fault, it was already too late.</p>
                      <p>Earth still isn't safe. But the Sojourner presses on, carrying what you've learned to the
                      next world, and the next, in search of an answer that actually works.</p>
                      <p>One thing is clear: whatever solution Earth needs, it must be pursued more carefully than
                      this civilization pursued theirs.</p>`,
            cta: 'Back to menu'
        }],
        ['reset cut', {
            title: 'Reset the class under test',
            content: `<p>This will reset your edits to the class under test. Are you sure you want to continue?</p>`,
            cta: 'Keep my changes'
        }],
        ['wait', {
            title: 'Hold on',
            content: `<p>{for}&hellip;</p><p>Please wait while the server is processing your request.</p>`,
            cta: 'Close'
        }],
    ]);

    /**
     * Per-room intro popups for the debugging strand. Each value is a single
     * PopupText (one step) or an array of PopupText (multistep) — author per room.
     * @type {Map<number, PopupText|array<PopupText>>}
     */
    static #debugRoomIntros = new Map([
        [1, [
            {
                title: 'Debugging the Artifact Analyzer',
                content: `<p>The <strong>ArtifactAnalyzer</strong> has a bug in it.</p>
                          <p>You'll have to <strong>debug</strong> it: narrow the problem down step by
                          step, the same way you traced the blocked pipes back in the temple. Changing code on
                          a blind guess rarely ends well - observe first, then fix what the evidence points to.</p>`,
                cta: 'Next',
            },
            {
                title: 'The Left Editor',
                content: `<p>The editor on the left holds the <strong>class under test (CUT)</strong> -
                          the ArtifactAnalyzer's code, bug included. This is where your fix goes.</p>
                          <p>You can edit it, but the class has to <strong>stay intact</strong>: it must remain a
                          complete, working component. Deleting or gutting code to dodge the bug won't get you
                          through - hidden tests check that the analyzer still does its job.</p>`,
                cta: 'Next',
            },
            {
                title: 'The Right Editor',
                content: `<p>The editor on the right is a <strong>runner</strong> - your scratch pad. What it
                          contains is entirely up to you, as long as it has no programming errors: on Run or
                          Debug it is compiled and executed top to bottom.</p>
                          <p>This first runner already calls the analyzer's six steps one by one, with a comment
                          under each stating what it <em>should</em> return. Four of them share a single
                          variable that gets overwritten every time, so the debugger only ever shows you its latest
                          value - to catch an earlier one, stop execution on that exact line before the next step
                          overwrites it.</p>
                          <p>You don't have to check all six steps one by one: splitting the chain into halves
                          with breakpoints is a good starting point. The comments are your map for this first
                          machine - the later ones hand you less and less, until you decide for yourself what
                          to observe.</p>`,
                cta: 'Next',
            },
            {
                title: 'Breakpoints',
                content: `<p>Click onto a line number to set a <strong>breakpoint</strong>. Hit
                          <strong>Debug</strong> instead of Run, and execution will pause there so you can
                          inspect variables and step through the code line by line. You can set multiple of these
                          and continue to the next one.</p>
                          <p>Keep in mind: a breakpoint only pauses the program when the running code actually
                          <em>reaches</em> its line. On a line the run never executes, it never fires.</p>
                          <p>Narrow it down, fix the bug in the CUT, then hit <strong>Run</strong> to
                          confirm the checks pass.</p>`,
                cta: `Let's start!`,
            },
        ]],
        [2, {
            title: 'Debugging the Vent Control',
            content: `<p>The <strong>VentControl</strong> cycles the growth bay's vent: open for the day, sealed
                      for the night while the power - and with it the air scrubber - is down. Its self-test just
                      failed: at the end of the cycle the vent is still open, and the power flag reads true again
                      on its own.</p>
                      <p>The runner still reads the bay's state after every step, so the observing is done for you -
                      but it keeps only the latest reading, not the whole day: the two variables get overwritten
                      every time. Working out what those states are <em>supposed</em> to be at each point is not
                      done for you: the runner leaves you the lines to fill in. Step through with <strong>Debug</strong>
                      and track both values as the cycle runs, the way you tracked state in the temple. Find the
                      first step where one of them turns into something it shouldn't, fix it, then hit <strong>Run</strong>
                      to confirm the fix against the
                      hidden tests.</p>`,
            cta: 'Start Debugging',
        }],
        [3, {
            title: 'Debugging the Plant Containment',
            content: `<p>The <strong>PlantContainment</strong> mixes the recovered agent into a dose and feeds it
                      to the sprout in the growth bay. The sample doesn't react at all: the dose comes out
                      inert.</p>
                      <p>This bay logs nothing, and the runner only shows you the failure. Which steps you look at,
                      and where you set your breakpoints, is up to you now. Follow the
                      dose back through the steps that built it, the way you followed each ingredient back to its
                      source in the temple, then hit <strong>Run</strong> to confirm the fix against the hidden
                      tests.</p>`,
            cta: 'Start Debugging',
        }],
        [4, {
            title: 'Debugging the Teleport Beacon',
            content: `<p>The <strong>TeleportBeacon</strong> should open the departure window and lift the crew
                      off the planet, but the window stays shut, so the ship can't leave.</p>
                      <p>Nothing is prepared this time: the runner hands you the failure and nothing else. You decide
                      what the beacon should be doing, where you suspect it isn't, and how you check. Test your hunch
                      against the evidence, the way the hypothesis machine had you do in the temple.</p>
                      <p>One more thing: a symptom can have more than one cause, so the window may stay shut even
                      after a fix that was correct - if you only tweak things until the output looks right, other values might still fail. Keep going until the hidden tests pass on <strong>Run</strong>.</p>`,
            cta: 'Start Debugging',
        }],
    ]);

    /** @type {PopupText} Fallback when a room has no dedicated intro. */
    static #debugRoomIntroDefault = {
        title: 'Start Debugging',
        content: `<p>The component on the left contains a bug. Find it and fix it.</p>`,
        cta: 'Start Debugging',
    };

    /**
     * Per-component "you fixed it" popups for the debugging strand, keyed by the
     * component name the server reports in the ComponentFixedEvent. Keyed by component
     * rather than room because the progression may already have advanced when the
     * event arrives. Falls back to the generic 'component fixed' text.
     * @type {Map<string, PopupText|array<PopupText>>}
     */
    static #debugComponentFixed = new Map([
        ['ArtifactAnalyzer', {
            title: 'The reading holds up',
            content: `<p>The bug is fixed. The analyzer reports the artifact's exposure the way the sensor
                      actually measured it.</p>`,
            cta: 'Continue',
        }],
        ['VentControl', {
            title: 'The bay makes it through the night',
            content: `<p>The bug is fixed. The vent seals for the night and the power stays down, so the sprout
                      from the temple keeps the air it needs until morning.</p>`,
            cta: 'Continue',
        }],
        ['PlantContainment', {
            title: 'The sample responds',
            content: `<p>The bug is fixed. The dose comes out active, and the sample finally reacts to the agent
                      recovered from the temple.</p>`,
            cta: 'Continue',
        }],
        ['TeleportBeacon', {
            title: 'The departure window opens',
            content: `<p>The bug is fixed. The link holds, and the beacon opens the window that lifts the crew off
                      the planet.</p>`,
            cta: 'Continue',
        }],
    ]);

    /** @type {Popup} */
    static #instance = null;

    /** @type {Array<Function>} */
    #onClose = [];
    /** @type {Array<Function>} */
    #onTransitionEnd = [];
    /** @type {false|{key:string,index:number}} */
    #multistep = false;

    static get instance() {
        if (Popup.#instance === null) {
            Popup.#instance = new Popup();
        }
        return Popup.#instance;
    }

    constructor() {
        this.element = document.getElementById('popup');
        this.heading = this.element.querySelector('.heading');
        this.content = this.element.querySelector('.content');
        this.button  = this.element.querySelector('#continue-button');
        this.button.addEventListener('click', this.close.bind(this));
    }

    /**
     * @param {string} contentKey The identifier for the content to display
     * @param {Object} [params] Values for parameterized text messages (Not supported for multistep popups!)
     */
    open(contentKey, params = {}) {
        if (Popup.#text.has(contentKey) === false) {
            throw new Error(`No content found for key ${contentKey}`);
        }
        const text = Popup.#text.get(contentKey);
        if (Array.isArray(text)) {
            this.#multistep = { key: contentKey, index: 0 };
            this.#renderMultiStep();
        } else {
            this.#multistep = false;
            this.#render(this.#applyParams(text, params));
        }
        return this;
    }
    
    openDebugRoomIntro(room) {
        const text = Popup.#debugRoomIntros.get(room) ?? Popup.#debugRoomIntroDefault;
        if (Array.isArray(text)) {
            this.#multistep = { key: room, index: 0, source: Popup.#debugRoomIntros };
            this.#renderMultiStep();
        } else {
            this.#multistep = false;
            this.#render(text);
        }
        return this;
    }

    openDebugComponentFixed(componentName) {
        const text = Popup.#debugComponentFixed.get(componentName);
        if (text === undefined) {
            return this.open('component fixed');
        }
        if (Array.isArray(text)) {
            this.#multistep = { key: componentName, index: 0, source: Popup.#debugComponentFixed };
            this.#renderMultiStep();
        } else {
            this.#multistep = false;
            this.#render(text);
        }
        return this;
    }

    #renderMultiStep() {
        if (this.#multistep === false) return;
        const source = this.#multistep.source ?? Popup.#text;
        const text = source.get(this.#multistep.key)[this.#multistep.index];
        this.#render(text);
    }

    /**
     * @param {PopupText} text
     */
    #render(text) {
        this.heading.innerText = text.title;
        this.content.innerHTML = text.content;
        this.button.innerText = text.cta;
        if (text.btnClass) {
            this.button.classList.add(text.btnClass);
            this.onTransitionEnd(() => this.button.classList.remove(text.btnClass));
        }
        this.element.setAttribute('aria-hidden', 'false');
    }

    close() {
        if (this.#multistep === false) {
            this.#close();
        } else {
            const source = this.#multistep.source ?? Popup.#text;
            const steps = source.get(this.#multistep.key).length;
            this.#multistep.index++;
            if (this.#multistep.index < steps) {
                this.element.setAttribute('aria-hidden', 'true'); // animate out
                this.element.addEventListener('transitionend', this.#renderMultiStep.bind(this), { once: true });
            } else {
                this.#multistep = false;
                this.#close();
            }
        }
    }

    #close() {
        this.element.addEventListener('transitionend', () => {
            this.#onTransitionEnd.forEach(fn => fn());
            this.#onTransitionEnd = [];
        }, { once: true });

        this.element.setAttribute('aria-hidden', 'true');
        this.#onClose.forEach(fn => fn());
        this.#onClose = [];
    }

    /** @param {Function} fn */
    onClose(fn) {
        this.#onClose.push(fn);
    }

    /** @param {Function} fn */
    onTransitionEnd(fn) {
        this.#onTransitionEnd.push(fn);
    }

    /**
     * @param {string} text - The text for the button
     * @param {Function} callback - The function to execute when the button is clicked
     * @param {string[]} [className] - Additional classes for the button
     * @param {boolean} [before] - Insert before the default button
     */
    addButton(text, callback, className = [], before = true) {
        const button = document.createElement('button');
        button.innerText = text;
        button.addEventListener('click', () => {
            this.onTransitionEnd(callback.bind(this));
            this.close();
        });
        button.classList.add(...className, 'button');
        if (before) {
            this.button.parentNode.insertBefore(button, this.button);
        } else {
            this.button.parentNode.appendChild(button);
        }
        this.onClose(button.remove.bind(button));
    }

    #applyParams(text, params) {
        const keys = Object.keys(params);
        if (keys.length > 0) {
            const regex = new RegExp(`{(${keys.join('|')})}`, 'g');
            text.title = text.title.replace(regex, (match, key) => params[key]);
            text.content = text.content.replace(regex, (match, key) => params[key]);
            text.cta = text.cta.replace(regex, (match, key) => params[key]);
        }
        return text;
    }
}
