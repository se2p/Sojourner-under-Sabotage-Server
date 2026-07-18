import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArtifactAnalyzerTest {


    @Test
    public void testFilterDropoutsKeepsValidReadings() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertArrayEquals(new int[] {214, 216, 215}, analyzer.filterDropouts(new int[] {214, -1, 216, -3, 215}));
    }

    @Test
    public void testFilterDropoutsOnDeadSensorLine() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertArrayEquals(new int[] {}, analyzer.filterDropouts(new int[] {-1, -2, -1}));
    }

    @Test
    public void testAverageReading() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(20, analyzer.averageReading(new int[] {10, 20, 30}));
    }

    @Test
    public void testAverageOfSingleReading() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(215, analyzer.averageReading(new int[] {215}));
    }

    @Test
    public void testNormalizeFullScale() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(1.0, analyzer.normalize(1023), 0.001);
    }

    @Test
    public void testNormalizeZeroReading() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(0.0, analyzer.normalize(0), 0.001);
    }

    @Test
    public void testCalibrateAddsTheOffset() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);
        assertEquals(0.215, analyzer.calibrate(0.21), 0.0001);
    }

    @Test
    public void testCalibrateWithZeroOffsetChangesNothing() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(0.21, analyzer.calibrate(0.21), 0.0001);
    }

    @Test
    public void testToPercentScalesUp() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(25.0, analyzer.toPercent(0.25), 0.001);
    }

    @Test
    public void testToPercentOfZero() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(0.0, analyzer.toPercent(0.0), 0.001);
    }

    @Test
    public void testClassifySharpBand() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals("SHARP", analyzer.classify(21.52));
        assertEquals("SHARP", analyzer.classify(19.5));
        assertEquals("SHARP", analyzer.classify(23.5));
    }

    @Test
    public void testClassifyRejectsOtherBands() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals("UNDEREXPOSED", analyzer.classify(15.9));
        assertEquals("FAINT", analyzer.classify(16.0));
        assertEquals("OVEREXPOSED", analyzer.classify(23.6));
    }
}