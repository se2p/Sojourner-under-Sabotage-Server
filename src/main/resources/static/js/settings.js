class Settings {
    static #keys = {
        codeEditorIntroductionShown: 'codeEditorIntroductionShown',
        debugRoomIntrosShown: 'debugRoomIntrosShown'
    }
    static #instance = null;
    #settings = null;

    static get instance() {
        return this.#instance = this.#instance ?? new Settings();
    }

    static #check(key) {
        if (!Object.values(Settings.#keys).includes(key)) {
            throw new Error(`Invalid key: ${key}`);
        }
    }

    static get keys() {
        return Settings.#keys;
    }

    async get(key) {
        Settings.#check(key);
        if (this.#settings === null) await this.load();
        return this.#settings[Settings.#keys[key]];
    }

    async set(key, value) {
        Settings.#check(key);
        if (this.#settings === null) await this.load();
        this.#settings[Settings.#keys[key]] = value;
        return fetch(`${apiUrl}/settings`, {
            method: "PUT",
            headers: jsonHeader,
            body: JSON.stringify(this.#settings)
        });
    }

    load() {
        return new Promise((resolve, reject) => {
            fetch(`${apiUrl}/settings`, {headers: jsonHeader})
                .then(response => response.json())
                .then(data => {
                    this.#settings = data;
                    resolve(data);
                })
                .catch(error => {
                    console.error("Error loading settings", error);
                    reject(error);
                });
        });
    }
}
