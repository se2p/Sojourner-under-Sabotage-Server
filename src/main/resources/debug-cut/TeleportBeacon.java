public class TeleportBeacon {

    // The return beacon opens the departure window that lifts
    // the crew off the planet.

    // The crystal only holds a charge while it is blue.
    public String stabilizeCrystal(String crystalColor) {
        if (crystalColor.equals("blue")) {
            return "CHARGED";
        }
        return "EMPTY";
    }

    // The gate only lines up with the ship's orbit while it
    // points north.
    public String alignGate(String direction) {
        if (direction.equals("north")) {
            return "ALIGNED";
        }
        return "MISALIGNED";
    }

    // Clearance level 3 is the lowest level a crewed jump is
    // allowed to run on; below that the code stays blocked.
    public String generateSafetyCode(int level) {
        if (level > 3) {
            return "CLEAR";
        }
        return "BLOCKED";
    }

    // The link only holds when the crystal is charged, the
    // gate is aligned and the safety code is clear.
    public String createTeleportLink(String energySignal, String gateAlignment, String safetyCode) {
        if (energySignal.equals("CHARGED")
                && gateAlignment.equals("ALIGNED")
                && safetyCode.equals("CLEAR")) {
            return "STABLE";
        }
        return "FAILED";
    }

    // The window only opens over a stable link.
    public boolean openDepartureWindow(String linkStatus) {
        return linkStatus.equals("STABLE");
    }

    // Full sequence: take the three readings, build the link
    // from them and open the departure window over it.
    public boolean canDepartPlanet() {
        String energySignal = stabilizeCrystal("blue");
        String gateAlignment = alignGate("north");
        String safetyCode = generateSafetyCode(3);

        String linkStatus = createTeleportLink(gateAlignment, energySignal, safetyCode);
        return openDepartureWindow(linkStatus);
    }
}
