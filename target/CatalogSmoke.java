import com.spaceconquest.engine.*;
class CatalogSmoke {
    public static void main(String[] args) throws Exception {
        var state = new GalaxyGenerator().generateGameState(2, 1, GameStartScenario.values()[0]);
        System.out.println("Generated facilities: " + state.industrialFacilities().size());
    }
}
