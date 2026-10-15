// VentControl runs the growth chamber's air cycle: power up in the morning,
// vent OPEN while the air reads fresh, then night mode - power down, vent
// SEALED. At the end of the cycle the vent is still OPEN and power reads true.
VentControl vents = new VentControl();

// expected at the start: power=false, ventState=SEALED
boolean power = vents.hasPower();
String ventState = vents.getVentState();

vents.activatePower();
power = vents.hasPower();
ventState = vents.getVentState();

vents.readAirSensor();
power = vents.hasPower();
ventState = vents.getVentState();

vents.updateVent();
power = vents.hasPower();
ventState = vents.getVentState();

vents.enterNightMode();
power = vents.hasPower();
ventState = vents.getVentState();

vents.updateVent();
power = vents.hasPower();
ventState = vents.getVentState();

// expected after the full cycle: power=false, ventState=SEALED
