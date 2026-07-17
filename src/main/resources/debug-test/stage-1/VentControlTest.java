import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VentControlTest {

    @Test
    public void testVentOpensDuringTheDay() {
        VentControl vents = new VentControl();
        vents.activatePower();
        vents.readAirSensor();
        vents.updateVent();
        assertEquals("OPEN", vents.getVentState());
        assertTrue(vents.hasPower());
    }

    @Test
    public void testVentSealsForTheNight() {
        VentControl vents = new VentControl();
        vents.activatePower();
        vents.readAirSensor();
        vents.updateVent();
        vents.enterNightMode();
        vents.updateVent();
        assertEquals("SEALED", vents.getVentState());
        assertFalse(vents.hasPower());
    }

    @Test
    public void testVentStaysSealedWithoutPower() {
        VentControl vents = new VentControl();
        vents.readAirSensor();
        vents.updateVent();
        assertEquals("SEALED", vents.getVentState());
        assertFalse(vents.hasPower());
    }

    @Test
    public void testVentStaysSealedWithoutFreshAir() {
        VentControl vents = new VentControl();
        vents.activatePower();
        vents.updateVent();
        assertEquals("SEALED", vents.getVentState());
        assertTrue(vents.hasPower());
    }

    @Test
    public void testUpdateVentLeavesPowerUntouched() {
        VentControl vents = new VentControl();
        vents.updateVent();
        assertFalse(vents.hasPower());
        vents.activatePower();
        vents.updateVent();
        assertTrue(vents.hasPower());
    }
}
