// The stone tablet is on the scanner bed and the optics check out,
// so the analyzer should report "SHARP" and start decoding it.
ArtifactAnalyzer analyzer = new ArtifactAnalyzer(0.005);

// One scan pass: eight readings, two of them sensor dropouts.
int[] scanPass = {214, -1, 216, 213, -1, 217, 215, 215};

// The six steps, called one after another.
int[] filtered = analyzer.filterDropouts(scanPass);
// expected: [214, 216, 213, 217, 215, 215], the two dropouts dropped

double temp = analyzer.averageReading(filtered);
// expected: 215, the six readings sum up to 1290

temp = analyzer.normalize(temp);
// expected: about 0.2102, that is 215 out of 1023

temp = analyzer.calibrate(temp);
// expected: about 0.2152, the offset of 0.005 added on top

temp = analyzer.toPercent(temp);
// expected: about 21.52

String quality = analyzer.classify(temp);
// expected: SHARP, because 21.52 lies between 19.5 and 23.5