class EventSystem {

  constructor() {
    /** @type {Map<String, Array<Function>>} */
    this.handlers = new Map();
    this.stompClient = this.#initStompClient();
    this.stompClient.activate();
    this.queue = [];
    /** @type {SusEvent} */
    this.lastReceivedEvent = null;
  }

  #initStompClient() {
    if (typeof StompJs === 'undefined') {
      throw new Error('StompJs is not available');
    }

    const eventSystemInstance = this;
    const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const wsUrl = `${wsProtocol}://${window.location.host}${window.ctx}websocket`;
    const client = new StompJs.Client({
      brokerURL: wsUrl,
      connectHeaders: window.csrfHeader,
      debug: function (str) {
        console.log(str);
      },
      reconnectDelay: 5e3,
      heartbeatIncoming: 4e3,
      heartbeatOutgoing: 4e3,
    });

    // this will be executed after a (re)connect
    // all subscribes must be done in this callback
    client.onConnect = function (frame) {
      console.log("StompJs connected to broker over ws");

      client.subscribe('/user/queue/events', (message) => {
        const event = JSON.parse(message.body);
        eventSystemInstance.#handleEvent(event);
      });

      setTimeout(() => {
        if (this.lastReceivedEvent?.timestamp) {
          const lastTimestamp = eventSystemInstance.lastReceivedEvent.timestamp;
          fetch(`${apiUrl}/resend-events/${lastTimestamp}`, {headers: authHeader})
              .then(r => r.text().then(console.log)).catch(console.error);
          console.log(`Requested resend of events since ${lastTimestamp}`);
        }

        while (eventSystemInstance.queue.length > 0) {
          const event = eventSystemInstance.queue.shift();
          eventSystemInstance.sendEvent(event);
        }
      }, 100);
    };

    client.onStompError = function (frame) {
      // Will be invoked in case of error encountered at Broker
      // Bad login/passcode typically will cause an error
      // Complaint brokers will set `message` header with a brief message.
      // The Body may contain details.
      // Compliant brokers will terminate the connection after any error
      console.log('Broker reported error: ' + frame.headers['message']);
      console.log('Additional details: ' + frame.body);
    };

    return client;
  }

  #handleEvent(event) {
    if (!event) {
      console.error("Received empty event", event);
      return;
    }
    // Drop events of the other strand
    if (event.mode && window.gameMode && event.mode !== window.gameMode) {
      console.log(`Ignoring ${event.type} for strand ${event.mode}`);
      return;
    }
    const eventType = event.type.replace(/^(\.)/, '');
    if (this.handlers.has(eventType)) {
      this.handlers.get(eventType).forEach((handler) => handler(event));
    }
    if (this.handlers.has('*')) {
      this.handlers.get('*').forEach((handler) => handler(event));
    }
    this.lastReceivedEvent = event;
  }

  /**
   * Register a handler for a specific event type
   * @param {String} eventType the name of the event type or * for all events.
   *                           Can contain a leading dot that will be removed.
   * @param {Function} eventHandler the handler function
   */
  registerHandler(eventType, eventHandler) {
    eventType = eventType.replace(/^(\.)/, '');
    if (this.handlers.has(eventType)) {
      this.handlers.get(eventType).push(eventHandler);
    } else {
      this.handlers.set(eventType, [eventHandler]);
    }
  }

  disconnect() {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
    console.log("Disconnected");
  }

  /**
   * Send an event to the server (and to client listeners if they subscribe to client events)
   *
   * @param {SusEvent} event the event to send
   */
  sendEvent(event) {
    if (!event) event = {};
    if (!event.timestamp) event.timestamp = Date.now();

    if (this.stompClient.connected) {
      this.stompClient.publish({
        destination: '/app/events',
        body: JSON.stringify(event),
        headers: window.gameMode ? {'game-mode': window.gameMode} : {},
        skipContentLengthHeader: true,
      });
    } else {
      this.queue.push(event);
    }

    this.#handleEvent(event);
  }
}

class SusEvent {
  timestamp;
  type;
}

class RoomUnlockedEvent extends SusEvent {
  static type = ".RoomUnlockedEvent";
  type = RoomUnlockedEvent.type;
  /** @type {Number} */
  roomId;

  /**
   * @param {Number} roomId the room id
   */
  constructor(roomId) {
    super();
    this.roomId = roomId;
  }
}

class ComponentTestsActivatedEvent extends SusEvent {
  static type = ".ComponentTestsActivatedEvent";
  type = ComponentTestsActivatedEvent.type;
  /** @type {String} */
  componentName;

  constructor(componentName) {
    super();
    this.componentName = componentName;
  }
}

class GameStartedEvent extends SusEvent {
  static type = ".GameStartedEvent";
  type = GameStartedEvent.type;
}

class ConversationFinishedEvent extends SusEvent {
  static type = ".ConversationFinishedEvent";
  type = ConversationFinishedEvent.type;
}

class DebugStartEvent extends SusEvent {
  static type = ".DebugStartEvent";
  type = DebugStartEvent.type;
  /** @type {String} */
  componentName;

  constructor(componentName) {
    super();
    this.componentName = componentName;
  }
}

class PuzzleSolvedEvent extends SusEvent {
  static type = ".PuzzleSolvedEvent";
  type = PuzzleSolvedEvent.type;
}

class TempleEnteredEvent extends SusEvent {
  static type = ".TempleEnteredEvent";
  type = TempleEnteredEvent.type;
}

class GameProgressionChangedEvent extends SusEvent {
  static type = ".GameProgressionChangedEvent";
  type = GameProgressionChangedEvent.type;
  /** @type {UserGameProgressionDTO} */
  progression;

  constructor(progression) {
    super();
    this.progression = progression;
  }
}
