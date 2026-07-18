class OffViewportInfo {
    constructor(selector) {
        this.container = document.querySelector(selector);
        this.objective = this.container.querySelector('.visible-content');
        this.hoverText = this.container.querySelector('.hover-content');

        this.container.addEventListener('mouseenter', this.show.bind(this));
        this.container.addEventListener('mouseleave', this.hide.bind(this));
        this.hide();

        const unity = document.getElementById('unity-canvas');
        this.container.addEventListener('click', () => unity.focus());
    }

    hide() {
        this.container.style.top = (-1 * this.hoverText.getBoundingClientRect().height) + 'px';
        this.container.classList.remove('is-expanded');
    }

    show() {
        this.container.style.top = '0';
        this.container.classList.add('is-expanded');
    }

    playUpdateAnimation() {
        this.show();
        setTimeout(this.hide.bind(this), 1e3);
    }
}

class ObjectiveDisplay extends OffViewportInfo {
    
    static objectives = {
        Debugging: {
            DOOR: {
                // Debug DOOR is the ship intro + trip down, not the testing wire minigame.
                default: {
                    objective: 'Travel to the temple',
                    details: '<p>Talk to the robot, then step onto the teleporter pad to travel ' +
                        'down to the temple on the planet below.</p>',
                },
                1: {
                    objective: 'Talk to the robot and explore your surroundings',
                    details: '<p>Talk to the robot to find out what\'s going on, then have a look around ' +
                        'the ship. Use the telescope to scan the planet; once the scan finishes, step onto ' +
                        'the teleporter pad to travel down.</p>',
                },
            },
            TALK: {
                // TALK = the planet-outside phase; entering the temple ends it. So these say
                // "head to the entrance", and they must not name what's found inside — the
                // authored dialogue (Debug.unity) keeps each temple's discovery a surprise.
                1: {
                    objective: 'You\'ve landed on the planet. Talk to the robot and head north.',
                    details: '<p>You\'ve landed on the planet, right by the first temple. Talk to the ' +
                        'robot, then head north to the temple entrance. There are plants and a few ' +
                        'other structures nearby if you want to look around first.</p>',
                },
                2: {
                    objective: 'Head to the second temple',
                    details: '<p>Talk to the robot, then make your way to the temple entrance in the north.</p>',
                },
                3: {
                    objective: 'Head to the third temple',
                    details: '<p>Talk to the robot, then head north to the temple entrance.</p>',
                },
                4: {
                    objective: 'Head to the last temple',
                    details: '<p>Talk to the robot, then find the temple entrance. This structure ' +
                        'is more secluded than all the others. Whatever happened on this planet, the ' +
                        'answer should be inside.</p>',
                },
            },
            PUZZLE: {
                // default: { objective: '…', details: '<p>…</p>' },
            },
            DEBUGGING: {
                // Careful: an override here replaces BOTH beats (navigation + active).
                // Room-specific texts for the active beat belong in twoPhase.DEBUGGING.active.
            },
        },
        Testing: {},
    };
    
    static twoPhase = {
        PUZZLE: {
            // PUZZLE only starts once the player walks into the temple (TempleEnteredEvent),
            // so this is always the in-temple objective; outside is covered by TALK.
            navigation: () => ({
                objective: 'Make your way further into the temple',
                details: '<p>The temple holds a single puzzle station. Interact with it and solve the puzzle ' +
                    'to open the hidden chamber at the back.</p>' +
                    '<p>Take a close look at what the builders left there and take it with you, then step onto ' +
                    'the portal back to the ship.</p>',
            }),
            active: () => ({
                objective: 'Solve the puzzle',
                details: '<p>Work through the puzzle on screen. Use the information icons for further descriptions of each component</p>',
            }),
        },
        DEBUGGING: {
            // The component consoles all sit on the ship;
            navigation: component => ({
                objective: `Go to the ${component}`,
                details: `<p>The ${component} is on the ship. Find the ${component} and interact with it.</p>`,
            }),
            active: (component, room) => {
                switch (room) {
                    case 1: return {
                        objective: 'Find the step that corrupts the value',
                        details: '<p>Same idea as the leaking pipes: the analyzer\'s scan reading flows through six ' +
                            'steps, and one of them corrupts it. From the outside you only see the wrong result at ' +
                            'the very end.</p><p>' +
                            'The runner on the right already calls all six steps, with a note underneath each for ' +
                            'what it should return - but four of them share one variable that keeps ' +
                            'getting overwritten, so the debugger only shows you its latest value. Press <em>Debug</em> ' +
                            'and stop on the exact line you want to check before the next step overwrites it. Fastest ' +
                            'is to check the middle first: every check halves the search. Click ' +
                            'left of a line number to set a breakpoint and stop right there.</p><p>' +
                            'Fix that step in the code on the left, then press <em>Run</em>: the component is repaired ' +
                            'once the hidden tests pass.</p>',
                    };
                    case 2: return {
                        objective: 'Find the step that corrupts the state',
                        details: '<p>Just like watching the state change in the temple, the bay\'s state walks ' +
                            'through the day cycle step by step: power up, read the sensor, open the vent, night ' +
                            'mode, seal the vent. At the end the vent should be sealed and the power off - instead ' +
                            'it is still open, and the power flag flipped back on by itself.</p><p>' +
                            'The runner on the right reads the bay\'s power and vent state after each step, ' +
                            'overwriting the same two variables every time. Press <em>Debug</em> and step through ' +
                            'line by line, tracking both values as the cycle runs - the first step where one of ' +
                            'them turns into something it shouldn\'t is where the bug lives. Fix it in the code on ' +
                            'the left, then press ' +
                            '<em>Run</em>: the component is repaired once the hidden tests pass.</p>',
                    };
                    case 3: return {
                        objective: 'Trace the inert dose back to its source',
                        details: '<p>Like tracing a mixture back through the vats in the temple, the bay builds the ' +
                            'sample\'s dose over a chain of steps, each one handing its return value to the next. ' +
                            'The dose comes out inert, so the sample doesn\'t react - but the step that reports the ' +
                            'failure is only the last one in the chain.</p><p>' +
                            'Set a breakpoint and press <em>Debug</em>, then work backwards: check what the step ' +
                            'before returned, and keep going back until you find the first one that returns ' +
                            'something it shouldn\'t. Each method\'s comment says what it is supposed to return. ' +
                            'Fix that step in the code on the left, then press <em>Run</em>: the component is ' +
                            'repaired once the hidden tests pass.</p>',
                    };
                    case 4: return {
                        objective: 'Find out why the departure window stays shut',
                        details: '<p>Like the hypothesis machine in the temple, the beacon takes several readings and ' +
                            'builds the teleport link from them - any of them could be the reason the link comes out ' +
                            'FAILED. Form a hypothesis about which one is to blame, then try to refute it: predict ' +
                            'what you must see if it is true, and check. An observation that contradicts the ' +
                            'prediction rules that hypothesis out, form the next one and test again.</p><p>' +
                            'Set a breakpoint in <em>canDepartPlanet</em>, press <em>Debug</em> and compare each ' +
                            'value against what the method\'s comment says it should be; if the readings look right ' +
                            'but the link still fails, step into <em>createTeleportLink</em> and check what its ' +
                            'parameters actually carry. More than one thing is wrong, so keep testing after your ' +
                            'first fix. Fix the code on the left, then press <em>Run</em>: the component is repaired ' +
                            'once the hidden tests pass.</p>',
                    };
                    default: return {
                        objective: 'Find the bug and fix it',
                        details: '<p>The component\'s code (on the left) contains a bug. Track it down the way the puzzle ' +
                            'taught you.</p><p>' +
                            'Click left of a line number to set a breakpoint, press <em>Debug</em> and inspect the ' +
                            'variables at each stop.</p><p>' +
                            'Fix the bug in the code on the left, then press <em>Run</em>: the component is repaired ' +
                            'once the hidden tests pass.</p>',
                    };
                }
            },
        },
    };

    constructor(selector) {
        super(selector);

        /** @type {UserGameProgressionDTO | null} the last progression we rendered for */
        this.current = null;
        /** @type {boolean} whether the player has opened the puzzle / debugger for the current state */
        this.interactionOpen = false;

        es.registerHandler(GameProgressionChangedEvent.type, this.gameProgressionChanged.bind(this));
    }
    
    getObjective(mode, status, room) {
        const forStatus = ObjectiveDisplay.objectives[mode]?.[status];
        return forStatus?.[room] ?? forStatus?.default;
    }

    /**
     * @param {string} objective
     * @param {string} details
     */
    setObjective(objective, details) {
        this.objective.innerHTML = objective;
        this.hoverText.innerHTML = details;
        this.playUpdateAnimation();
    }

    getRoomName(roomId) {
        const rooms = [
            'Cryo Chamber', 'Engine Compartment', 'Farm', 'Defense', 'Kitchen and Dining Room', 'Reactor',
            'Infirmary', 'Communication', 'Escape Pod'
        ];
        return rooms[roomId - 1];
    }

    /** @param {{progression:UserGameProgressionDTO}} evt */
    gameProgressionChanged(evt) {
        this.current = evt.progression;
        this.interactionOpen = false; // a fresh state always starts on the "go there" beat
        this.renderObjective();
    }

    setInteractionOpen(open) {
        this.interactionOpen = open;
        this.renderObjective();
    }

    renderObjective() {
        const progression = this.current;
        if (!progression) return;

        // A per-(mode, status, room) override wins over the inline defaults below.
        const override = this.getObjective(progression.mode, progression.status, progression.room);
        if (override) {
            this.setObjective(override.objective, override.details);
            return;
        }

        // Debug strand: PUZZLE and DEBUGGING split into a navigation and an active beat.
        const phase = ObjectiveDisplay.twoPhase[progression.status];
        if (progression.mode === 'Debugging' && phase) {
            const beat = (this.interactionOpen ? phase.active : phase.navigation)(progression.componentName, progression.room);
            this.setObjective(beat.objective, beat.details);
            return;
        }

        switch (progression.status) {
            case 'DOOR':
                this.setObjective(
                    'Fix the door',
                    '<p>The door to the next room is broken.<br> Find it and fix it by connecting the wires so that' +
                    'current can flow through them from the energy source to the door opening mechanism.</p>' +
                    '<p>Click on the wires to rotate them.</p>' +
                    '<p>Once the circuit is complete, the current can flow through the wires from the top left to ' +
                    'the bottom right and the door will open.</p>'
                );
                break;
            case 'TALK':
                this.setObjective(
                    'Talk to the robot',
                    'You\'re now in the '+this.getRoomName(progression.room)+'! ' +
                    'Talk to the robot and find out more about this room.'
                );
                break;
            case 'TEST':
                this.setObjective(
                    'Write tests for the component.',
                    '<p>Find the component and open the code editor.</p><p>' +
                    'Write tests for the component to make sure it works as expected. Using these tests, you\'ll be ' +
                    'able to automatically detect if the component breaks in the future.</p>' +
                    '<p>The lines highlighted in green are covered by your tests.<br><span id="coverage-info"></span></p>'
                );
                break;
            case 'TESTS_ACTIVE':
                this.setObjective(
                    'Find out more about this room',
                    'Your tests are now active, so you don\'t have to worry about the component breaking. ' +
                    'Once the component is attacked, your tests will detect it and you can fix it.'
                );
                break;
            case 'MUTATED':
                this.setObjective(
                    'Fix the mutated component',
                    '<p>The component has been attacked! Luckily your tests were in place and detected it soon enough. ' +
                    '</p><p>Find the mutated component and fix it.</p><p>' +
                    'The code was modified by the attacker, so you need to find the bug in it and fix it.</p>'
                );
                break;
            case 'DESTROYED':
                this.setObjective(
                    'Fix the destroyed component',
                    '<p>The component has been attacked. Your tests were not good enough to detect the mutation.' +
                    'Therefore, the component was destroyed.</p><p>' +
                    'The code was modified by the attacker, so you need to find the bug in it and fix it.</p><p>' +
                    'The robot will write some tests for you, which you can use to ensure that the component ' +
                    'is working as expected again.</p>'
                );
                break;
            case 'DEBUGGING':
                this.setObjective(
                    'Find the bug and fix it',
                    '<p>The components code (on the left) was mutated by the attacker. Find the bug and fix it.</p><p>' +
                    'You can use <code>System.out.println(...);</code> to print out values and strings while you\'re ' +
                    'debugging the code.</p><p> You can also write new or modify old tests to try out different scenarios.</p>'
                );
                break;
        }
    }
}
