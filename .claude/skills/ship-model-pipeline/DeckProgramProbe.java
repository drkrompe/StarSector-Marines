import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints the mod's deck program for one hull: the input to pass 4's scale.
 *
 * <pre>
 * java -cp "build/classes/java/main;&lt;starsector-core&gt;/starfarer.api.jar;&lt;starsector-core&gt;/log4j-1.2.9.jar;&lt;starsector-core&gt;/json.jar" \
 *     .claude/skills/ship-model-pipeline/DeckProgramProbe.java FRIGATE "Civilian Transport" 5 100 70
 * </pre>
 * Arguments: hull class, designation (from ship_data.csv), min crew, max crew, cargo.
 * One room cell is a square metre. Run against freshly compiled classes.
 */
public class DeckProgramProbe {
    public static void main(String[] args) {
        HullClass hullClass = HullClass.valueOf(args[0]);
        HullRole role = HullRole.fromDesignation(args[1]);
        int minCrew = Integer.parseInt(args[2]);
        int maxCrew = Integer.parseInt(args[3]);
        int cargo = Integer.parseInt(args[4]);
        List<RoomRecipe> rooms = DeckSizing.programFor(hullClass, role, minCrew, maxCrew, cargo);
        Map<String, int[]> rows = new LinkedHashMap<>();
        int total = 0;
        for (RoomRecipe room : rooms) {
            String key = room.purpose() + " " + room.shape().width() + "x" + room.shape().height();
            int[] row = rows.computeIfAbsent(key, k -> new int[2]);
            row[0]++;
            row[1] += room.area();
            total += room.area();
        }
        System.out.println(hullClass + " " + role + " crew " + minCrew + "-" + maxCrew + " cargo " + cargo);
        rows.forEach((key, row) -> System.out.println("  " + key + " x" + row[0] + " = " + row[1] + " m2"));
        float circulation = 1.40f; // DeckSizing.CIRCULATION (private)
        System.out.println("rooms " + total + " m2; x" + circulation + " circulation = "
                + Math.round(total * circulation) + " m2; x3.2 m deck height = "
                + Math.round(total * circulation * 3.2f) + " m3");
    }
}
