public class VentControl {

    private boolean power = false;
    private boolean airFresh = false;
    private String ventState = "SEALED";

    // Morning power-up of the bay.
    public void activatePower() {
        power = true;
    }

    // Night power-save: the whole bay, including the air
    // scrubber, powers down until morning.
    public void enterNightMode() {
        power = false;
    }

    // Polls the air quality sensor. The sensor line has read
    // fresh ever since the landing.
    public void readAirSensor() {
        airFresh = true;
    }

    // The vent may only be OPEN while the bay has power and
    // the air reads fresh. In every other state it must be
    // SEALED: without power the scrubber cannot filter what
    // comes through.
    public void updateVent() {
        if (power = true && airFresh) {
            ventState = "OPEN";
        } else {
            ventState = "SEALED";
        }
    }

    public boolean hasPower() {
        return power;
    }

    public String getVentState() {
        return ventState;
    }
}
