// The agent the Builders fed their plants is reconstructed, and the
// containment bay just mixed the first dose for the sprout in the growth
// bay. The sample didn't react at all: feedSample() reports false, so the
// dose came out inert.
//
// This runner observes nothing for you. The dose is built up over a chain
// of steps, each one handing its result to the next - which of them you
// look at, and how, is your call. Every method on the left says in its
// comment what it is supposed to return.
PlantContainment bay = new PlantContainment("WATER", "AGENT");

// expected true (the sample responds), actual false
boolean responded = bay.feedSample();
