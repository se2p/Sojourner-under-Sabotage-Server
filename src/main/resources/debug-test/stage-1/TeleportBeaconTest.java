import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TeleportBeaconTest {

    @Test
    public void testCanDepartPlanet() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertTrue(beacon.canDepartPlanet());
    }

    @Test
    public void testStabilizeCrystalChargesBlueCrystal() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("CHARGED", beacon.stabilizeCrystal("blue"));
        assertEquals("EMPTY", beacon.stabilizeCrystal("red"));
    }

    @Test
    public void testAlignGateAlignsToNorth() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("ALIGNED", beacon.alignGate("north"));
        assertEquals("MISALIGNED", beacon.alignGate("south"));
    }

    @Test
    public void testSafetyCodeClearsAtMinimumLevel() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("CLEAR", beacon.generateSafetyCode(3));
        assertEquals("CLEAR", beacon.generateSafetyCode(4));
        assertEquals("BLOCKED", beacon.generateSafetyCode(2));
    }

    @Test
    public void testCreateTeleportLinkHoldsWhenAllReadingsAreGood() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("STABLE", beacon.createTeleportLink("CHARGED", "ALIGNED", "CLEAR"));
    }

    @Test
    public void testCreateTeleportLinkFailsOnAnyBadReading() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("FAILED", beacon.createTeleportLink("EMPTY", "ALIGNED", "CLEAR"));
        assertEquals("FAILED", beacon.createTeleportLink("CHARGED", "MISALIGNED", "CLEAR"));
        assertEquals("FAILED", beacon.createTeleportLink("CHARGED", "ALIGNED", "BLOCKED"));
    }

    @Test
    public void testOpenDepartureWindowOnlyOverStableLink() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertTrue(beacon.openDepartureWindow("STABLE"));
        assertFalse(beacon.openDepartureWindow("FAILED"));
    }
}
