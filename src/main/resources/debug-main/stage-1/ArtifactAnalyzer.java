// The first stone tablet is on the scanner bed and the optics check out,
// so the analyzer should report "SHARP" and start decoding it. It doesn't.
//
// analyze() runs the readings through six steps:
//     filterDropouts -> averageReading -> normalize -> calibrate -> toPercent -> classify
//
// This runner already does the work for you, this one time: instead of
// calling analyze(), it calls the six steps one by one and keeps what each
// one returns in its own variable. Below every step is the value it is
// supposed to return, worked out from the comments in the analyzer on the
// left.
//
// Press Debug and step through the run: the debugger shows you every one of
// those variables as it is assigned. Compare the two from top to bottom. As
// long as they agree, that step is fine. The first step where they differ is
// where the reading gets corrupted, and everything after it only carries the
// damage along. Read that one method closely, fix it on the left, then press
// Run again: the analyzer counts as repaired once the hidden tests pass.
ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);

// One scan pass: eight readings, two of them sensor dropouts.
int[] scanPass = {214, -1, 216, 213, -1, 217, 215, 215};

int[] filtered = analyzer.filterDropouts(scanPass);
// expected: [214, 216, 213, 217, 215, 215], the two dropouts dropped

int average = analyzer.averageReading(filtered);
// expected: 215, the six readings sum up to 1290

double normalized = analyzer.normalize(average);
// expected: about 0.2102, that is 215 out of 1023

double calibrated = analyzer.calibrate(normalized);
// expected: about 0.2152, the offset of 0.005 added on top

double percent = analyzer.toPercent(calibrated);
// expected: about 21.52

String quality = analyzer.classify(percent);
// expected: SHARP, because 21.52 lies between 19.5 and 23.5

// Same exposure, reduced to a single clean reading. A scan of the same
// tablet should not change its verdict, so this is expected to be "SHARP" too.
String singleReadingQuality = analyzer.analyze(new int[]{215});
