// The beacon should open the departure window and lift the crew off the
// planet, but canDepartPlanet() keeps reporting false: the window stays
// shut, so the ship can't leave.
TeleportBeacon beacon = new TeleportBeacon();

// expected true, actual false
boolean departurePossible = beacon.canDepartPlanet();
