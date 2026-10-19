public class ArtifactAnalyzer {

    // The scanner's optical sensor reports a raw exposure
    // reading between 0 and SENSOR_MAX. A single scan pass
    // takes several readings in a row.
    private static final double SENSOR_MAX = 1023.0;

    // Readings below this value are sensor dropouts and do
    // not carry any exposure information.
    private static final int MIN_VALID_READING = 0;

    // A small correction applied to every scan to compensate
    // for sensor drift.
    private final double calibrationOffset;

    public ArtifactAnalyzer(double calibrationOffset) {
        this.calibrationOffset = calibrationOffset;
    }

    // Step 1: drop the dropouts, keep the usable readings.
    public int[] filterDropouts(int[] readings) {
        int count = 0;
        for (int i = 0; i < readings.length; i++) {
            if (readings[i] >= MIN_VALID_READING) {
                count++;
            }
        }
        int[] filtered = new int[count];
        int index = 0;
        for (int i = 0; i < readings.length; i++) {
            if (readings[i] >= MIN_VALID_READING) {
                filtered[index] = readings[i];
                index++;
            }
        }
        return filtered;
    }

    // Step 2: condense the usable readings of one scan pass
    // into a single exposure reading.
    public double averageReading(int[] readings) {
        int sum = 0;
        for (int i = 0; i < readings.length; i++) {
            sum = sum + readings[i];
        }
        return sum / (readings.length + 1);
    }

    // Step 3: scale the reading (0..SENSOR_MAX) down to a
    // 0.0..1.0 fraction.
    public double normalize(double reading) {
        return reading / SENSOR_MAX;
    }

    // Step 4: apply the calibration. The sensor reads
    // slightly low, so the offset is added on top of the
    // normalized value: a calibrated value is therefore
    // never smaller than the value that went in.
    public double calibrate(double normalized) {
        return normalized + calibrationOffset;
    }

    // Step 5: turn the 0.0..1.0 fraction into a percentage
    // (0..100).
    public double toPercent(double calibrated) {
        return calibrated * 100.0;
    }

    // Step 6: classify the exposure percentage into a
    // scan-quality band. Only a "SHARP" scan is good enough
    // to decode the engravings on an artifact.
    public String classify(double percent) {
        if (percent < 16.0) {
            return "UNDEREXPOSED";
        }
        if (percent < 19.5) {
            return "FAINT";
        }
        if (percent <= 23.5) {
            return "SHARP";
        }
        return "OVEREXPOSED";
    }
}
