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
    }

    @Test
    public void testLightEnzymeOnlyWeaklyChargesOtherBases() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("WEAK_ESSENCE", bay.chargeWithLightEnzyme("WATER"));
    }

    @Test
    public void testGrowthEssenceIsCharged() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("ESSENCE", bay.mixGrowthEssence());
    }

    @Test
    public void testGrowthEssenceIsWeakWithoutAgent() {
        PlantContainment bay = new PlantContainment("WATER", "NONE");
        assertEquals("WEAK_ESSENCE", bay.mixGrowthEssence());
    }

    @Test
    public void testMineralBinderIsMineral() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("MINERAL", bay.mixMineralBinder());
    }

    @Test
    public void testMineralBinderIgnoresStockAndAgent() {
        PlantContainment bay = new PlantContainment("MUD", "NONE");
        assertEquals("MINERAL", bay.mixMineralBinder());
    }

    @Test
    public void testDoseIsActiveWhenBothComponentsAreRight() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("DOSE_ACTIVE", bay.mixDose());
    }

    @Test
    public void testDoseIsInertWithoutAgent() {
        PlantContainment bay = new PlantContainment("WATER", "NONE");
        assertEquals("DOSE_INERT", bay.mixDose());
    }

    @Test
    public void testCombineActivatesTheDose() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("DOSE_ACTIVE", bay.combine("ESSENCE", "MINERAL"));
    }

    @Test
    public void testDoseIsInertWithWeakEssence() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertEquals("DOSE_INERT", bay.combine("WEAK_ESSENCE", "MINERAL"));
        assertEquals("DOSE_INERT", bay.combine("ESSENCE", "GRAVEL"));
    }

    @Test
    public void testSampleRespondsToActiveDose() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertTrue(bay.checkSampleResponse("DOSE_ACTIVE"));
    }

    @Test
    public void testSampleIgnoresInertDose() {
        PlantContainment bay = new PlantContainment("WATER", "AGENT");
        assertFalse(bay.checkSampleResponse("DOSE_INERT"));
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