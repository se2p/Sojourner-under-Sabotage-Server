package de.tim_greller.susserver.dto;

/**
 * The status of the game progress.
 */
public enum GameProgressStatus {
    DOOR,
    TALK,
    TEST,
    TESTS_ACTIVE,
    DESTROYED,
    MUTATED,
    DEBUGGING,
    PUZZLE;

    /**
     * Checks if the status is an initial status.
     * @return {@code true} if the status is TALK or TEST, {@code false} otherwise.
     */
    public boolean initial() {
        return this == DOOR || this == TALK || this == TEST;
    }

    /**
     * Checks if the component's tests are editable with the current progress.
     *
     * @return {@code true} if the tests are editable, {@code false} otherwise.
     */
    public boolean testEditable() {
        return this == TEST || this == DEBUGGING;
    }

    /**
     * Checks if the component's code is editable with the current progress.
     * @return {@code true} if the code is editable, {@code false} otherwise.
     */
    public boolean cutEditable() {
        return this == DEBUGGING;
    }

    /**
     * Checks if the component is ready for debugging.
     *
     * @return {@code true} if the component is mutated and debugging hasn't yet started, {@code false} otherwise.
     */
    public boolean readyForDebugging() {
        return this == DESTROYED || this == MUTATED;
    }
}
