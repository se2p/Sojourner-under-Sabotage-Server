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
    }

    @Test
    public void testStabilizeCrystalLeavesOtherCrystalsEmpty() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("EMPTY", beacon.stabilizeCrystal("red"));
        assertEquals("EMPTY", beacon.stabilizeCrystal("green"));
    }

    @Test
    public void testAlignGateAlignsToNorth() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("ALIGNED", beacon.alignGate("north"));
    }

    @Test
    public void testAlignGateMisalignsInOtherDirections() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("MISALIGNED", beacon.alignGate("south"));
        assertEquals("MISALIGNED", beacon.alignGate("east"));
    }

    @Test
    public void testSafetyCodeClearsAtMinimumLevel() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("CLEAR", beacon.generateSafetyCode(3));
        assertEquals("CLEAR", beacon.generateSafetyCode(4));
    }

    @Test
    public void testSafetyCodeBlocksBelowMinimumLevel() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertEquals("BLOCKED", beacon.generateSafetyCode(2));
        assertEquals("BLOCKED", beacon.generateSafetyCode(0));
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
    public void testDepartureWindowOpensOverStableLink() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertTrue(beacon.openDepartureWindow("STABLE"));
    }

    @Test
    public void testDepartureWindowStaysClosedOverFailedLink() {
        TeleportBeacon beacon = new TeleportBeacon();
        assertFalse(beacon.openDepartureWindow("FAILED"));
    }
}