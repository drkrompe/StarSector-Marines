package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Prototype authoring resolver: an ordered cell stroke and its working side select the modules.
 * Coordinates and turns are image-space (Y down, clockwise); placements convert at the renderer.
 */
public final class ConsoleBankLayout {
    public record Cell(int x, int y) {}
    public record Piece(Cell cell, String id, int clockwiseTurns) {}
    private ConsoleBankLayout() {}

    public static List<Piece> resolve(List<Cell> stroke, boolean closed, boolean operatorRight) {
        if (stroke.size() < (closed ? 4 : 2)) throw new IllegalArgumentException("Stroke is too short");
        if (new HashSet<>(stroke).size() != stroke.size()) throw new IllegalArgumentException("Repeated cell");
        List<Piece> result = new ArrayList<>();
        for (int i=0;i<stroke.size();i++) {
            Cell cell=stroke.get(i);
            Cell before=i>0 ? stroke.get(i-1) : closed ? stroke.get(stroke.size()-1) : null;
            Cell after=i+1<stroke.size() ? stroke.get(i+1) : closed ? stroke.get(0) : null;
            String shape; int turns;
            if (before == null) {
                shape=operatorRight ? "end-left" : "end-right";
                turns=direction(cell,after)+1;
            } else if (after == null) {
                shape=operatorRight ? "end-right" : "end-left";
                turns=direction(before,cell)-1;
            } else {
                int incoming=direction(before,cell), outgoing=direction(cell,after);
                int bend=Math.floorMod(outgoing-incoming,4);
                if (bend == 0) {
                    shape=i%2==0 ? "straight" : "straight-alt";
                    turns=outgoing+(operatorRight ? 0 : 2);
                } else {
                    if (bend == 2) throw new IllegalArgumentException("Stroke doubles back");
                    shape=(bend==1)==operatorRight ? "inside-corner" : "outside-corner";
                    int previousPort=(incoming+2)%4;
                    // Canonical elbow connects E (0) and S (1).
                    turns=(previousPort+1)%4==outgoing ? previousPort : outgoing;
                }
            }
            result.add(new Piece(cell,"console-bank."+shape,Math.floorMod(turns,4)));
        }
        return List.copyOf(result);
    }

    private static int direction(Cell a, Cell b) {
        int dx=b.x-a.x, dy=b.y-a.y;
        if (Math.abs(dx)+Math.abs(dy)!=1) throw new IllegalArgumentException("Stroke must be cardinal and contiguous");
        return dx==1 ? 0 : dy==1 ? 1 : dx==-1 ? 2 : 3;
    }
}
