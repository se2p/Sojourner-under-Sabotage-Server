import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AtmosphereAnalyzerTest {

    @Test
    public void testSafeO2Level() {
        AtmosphereAnalyzer analyzer = new AtmosphereAnalyzer(0.005);
        assertEquals("SAFE", analyzer.analyze(195));
    }

    @Test
    public void testCriticalO2Level() {
        AtmosphereAnalyzer analyzer = new AtmosphereAnalyzer(0.005);
        assertEquals("CRITICAL", analyzer.analyze(100));
    }

    @Test
    public void testHighO2Level() {
        AtmosphereAnalyzer analyzer = new AtmosphereAnalyzer(0.0);
        assertEquals("HIGH", analyzer.analyze(250));
    }

    @Test
    public void testNormalizeFullScale() {
        AtmosphereAnalyzer analyzer = new AtmosphereAnalyzer(0.0);
        assertEquals(1.0, analyzer.normalize(1023), 0.001);
    }
}