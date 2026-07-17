// The seedling from the temple is potted in the growth bay, and
// VentControl runs the bay's air cycle: power up in the morning, vent
// OPEN while the air reads fresh, then night mode - power down, vent
// SEALED, because the scrubber can't filter anything while the power is
// off. The bay's self-test below just failed: at the end of the cycle the
// vent is still OPEN, and power reads true even though night mode
// switched it off.
//
// No worked-out comparison this time. This runner walks the cycle step by
// step and keeps the bay's two readings after every step, so Debug shows
// you the whole day in one run. What those readings are supposed to be is
// yours to work out: fill in the lines below from the comments on the left
// BEFORE you step through, then compare them against what the debugger
// shows. The first step that disagrees with your prediction is where to
// look.
VentControl vents = new VentControl();

// expected at the start: power=false, ventState=SEALED
boolean powerAtStart = vents.hasPower();
String ventAtStart = vents.getVentState();

// expected after activatePower:
vents.activatePower();
boolean powerAfterPowerUp = vents.hasPower();
String ventAfterPowerUp = vents.getVentState();

// expected after readAirSensor:
vents.readAirSensor();
boolean powerAfterSensor = vents.hasPower();
String ventAfterSensor = vents.getVentState();

// expected after updateVent:
vents.updateVent();
boolean powerAfterDayVent = vents.hasPower();
String ventAfterDayVent = vents.getVentState();

// expected after enterNightMode:
vents.enterNightMode();
boolean powerAfterNightMode = vents.hasPower();
String ventAfterNightMode = vents.getVentState();

// expected after the second updateVent:
vents.updateVent();
boolean powerAfterNightVent = vents.hasPower();
String ventAfterNightVent = vents.getVentState();

// expected after the full cycle: power=false, ventState=SEALED
