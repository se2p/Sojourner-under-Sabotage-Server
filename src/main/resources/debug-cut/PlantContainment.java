public class PlantContainment {

    private final String stock;
    private final String agent;

    public PlantContainment(String stock, String agent) {
        this.stock = stock;
        this.agent = agent;
    }

    // The containment bay feeds the plant sample a dose of the
    // agent recovered from the temple. The sample only responds
    // to a dose that comes out active, and the bay's self-test
    // checks exactly that.
    public boolean feedSample() {
        String dose = mixDose();
        return checkSampleResponse(dose);
    }

    // Builds the finished dose out of its two components: the
    // growth essence and the mineral binder.
    public String mixDose() {
        String essence = mixGrowthEssence();
        String binder = mixMineralBinder();
        return combine(essence, binder);
    }

    // The growth essence: brew the base culture, then charge
    // it with the light enzyme.
    public String mixGrowthEssence() {
        String base = brewBaseCulture();
        return chargeWithLightEnzyme(base);
    }

    // The binder is a plain mineral suspension, no processing
    // needed.
    public String mixMineralBinder() {
        return "MINERAL";
    }

    // Brews the base culture: the nutrient stock is activated
    // with the recovered agent. Stock activated with "AGENT"
    // turns "BIOACTIVE"; with anything else the stock comes
    // back unchanged.
    public String brewBaseCulture() {
        if (stock.equals("AGENT")) {
            return "BIOACTIVE";
        }
        return stock;
    }

    // The light enzyme only takes on a bioactive base. Any
    // other base charges up to a weak essence.
    public String chargeWithLightEnzyme(String base) {
        if (base.equals("BIOACTIVE")) {
            return "ESSENCE";
        }
        return "WEAK_ESSENCE";
    }

    // The dose is only active if both components came out the
    // way they are specified.
    public String combine(String essence, String binder) {
        if (essence.equals("ESSENCE") && binder.equals("MINERAL")) {
            return "DOSE_ACTIVE";
        }
        return "DOSE_INERT";
    }

    // The sample only responds to an active dose.
    public boolean checkSampleResponse(String dose) {
        return dose.equals("DOSE_ACTIVE");
    }
}
