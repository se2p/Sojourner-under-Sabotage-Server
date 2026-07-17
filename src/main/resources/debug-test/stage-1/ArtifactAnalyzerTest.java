import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArtifactAnalyzerTest {

    @Test
    public void testSharpScan() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);
        assertEquals("SHARP", analyzer.analyze(new int[] {214, -1, 216, 213, -1, 217, 215, 215}));
    }

    @Test
    public void testSharpScanSingleReading() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);
        assertEquals("SHARP", analyzer.analyze(new int[] {215}));
    }

    @Test
    public void testUnderexposedScan() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);
        assertEquals("UNDEREXPOSED", analyzer.analyze(new int[] {100, -1, 104}));
    }

    @Test
    public void testOverexposedScan() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals("OVEREXPOSED", analyzer.analyze(new int[] {250, 250}));
    }

    @Test
    public void testFilterDropoutsKeepsValidReadings() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertArrayEquals(new int[] {214, 216, 215}, analyzer.filterDropouts(new int[] {214, -1, 216, -3, 215}));
    }

    @Test
    public void testAverageReading() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(20, analyzer.averageReading(new int[] {10, 20, 30}));
    }

    @Test
    public void testNormalizeFullScale() {
        ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.0);
        assertEquals(1.0, analyzer.normalize(1023), 0.001);
    }
}
