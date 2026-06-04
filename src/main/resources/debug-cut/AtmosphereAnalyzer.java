public class AtmosphereAnalyzer {

    private static final int SENSOR_MAX = 1023;

    private final double calibrationOffset;

    public AtmosphereAnalyzer(double calibrationOffset) {
        this.calibrationOffset = calibrationOffset;
    }

    public double normalize(int rawValue) {
        return rawValue / (double) SENSOR_MAX;
    }

    public double calibrate(double normalized) {
        return normalized + calibrationOffset;
    }

    public double toPercent(double calibrated) {
        return calibrated * 100.0;
    }

    public String classify(double percent) {
        if (percent < 16.0) return "CRITICAL";
        if (percent < 19.5) return "LOW";
        if (percent <= 23.5) return "SAFE";
        return "HIGH";
    }

    public String analyze(int rawValue) {
        double normalized = normalize(rawValue);
        double calibrated = calibrate(normalized);
        double percent = toPercent(calibrated);
        return classify(percent);
    }
}
