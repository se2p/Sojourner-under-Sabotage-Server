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
            cta: 'Close'
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

    #renderMultiStep() {
        if (this.#multistep === false) return;
        const text = Popup.#text.get(this.#multistep.key)[this.#multistep.index];
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
            const steps = Popup.#text.get(this.#multistep.key).length;
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
