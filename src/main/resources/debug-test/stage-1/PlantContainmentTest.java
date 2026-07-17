import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlantContainmentTest {

    @Test
    public void testBrewsBioactiveCultureWithAgent() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("BIOACTIVE", bay.brewBaseCulture());
    }

    @Test
    public void testStockStaysUnchangedWithoutAgent() {
        PlantContainment bay = new PlantContainment("WATER", "NONE");
        assertEquals("WATER", bay.brewBaseCulture());
    }

    @Test
    public void testLightEnzymeChargesBioactiveBase() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("ESSENCE", bay.chargeWithLightEnzyme("BIOACTIVE"));
        assertEquals("WEAK_ESSENCE", bay.chargeWithLightEnzyme("WATER"));
    }

    @Test
    public void testGrowthEssenceIsCharged() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("ESSENCE", bay.mixGrowthEssence());
    }

    @Test
    public void testDoseIsActiveWhenBothComponentsAreRight() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("DOSE_ACTIVE", bay.mixDose());
    }

    @Test
    public void testDoseIsInertWithWeakEssence() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("DOSE_INERT", bay.combine("WEAK_ESSENCE", "MINERAL"));
    }

    @Test
    public void testSampleRespondsToTheDose() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertTrue(bay.feedSample());
    }

    @Test
    public void testSampleDoesNotRespondWithoutAgent() {
        PlantContainment bay = new PlantContainment("WATER", "NONE");
        assertFalse(bay.feedSample());
    }
}
