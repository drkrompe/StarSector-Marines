package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.tilesetauthoring.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.tools.tilesetauthoring.ConsoleBankLayout.Piece;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleBankLayoutTest {
    @Test void turningStrokeHasMatchingPortsInEveryRotationAndWorkingSide() {
        for(boolean right : new boolean[]{true,false}) for(int turn=0;turn<4;turn++) {
            List<Cell> stroke=List.of(rotate(new Cell(0,1),turn),rotate(new Cell(0,0),turn),
                    rotate(new Cell(1,0),turn),rotate(new Cell(2,0),turn));
            List<Piece> pieces=ConsoleBankLayout.resolve(stroke,false,right);
            assertEquals(right ? "console-bank.inside-corner" : "console-bank.outside-corner",pieces.get(1).id());
            assertEquals(turn,pieces.get(1).clockwiseTurns());
            for(int i=0;i<pieces.size()-1;i++) {
                Cell a=pieces.get(i).cell(), b=pieces.get(i+1).cell();
                int direction=b.x()>a.x()?0:b.y()>a.y()?1:b.x()<a.x()?2:3;
                assertTrue(hasPort(pieces.get(i),direction));
                assertTrue(hasPort(pieces.get(i+1),(direction+2)%4));
            }
        }
    }

    @Test void closedIslandHasFourOutsideCornersAndNoEndCaps() {
        List<Piece> pieces=ConsoleBankLayout.resolve(List.of(new Cell(0,0),new Cell(1,0),
                new Cell(1,1),new Cell(0,1)),true,false);
        assertTrue(pieces.stream().allMatch(p->p.id().endsWith("outside-corner")));
        assertEquals(List.of(0,1,2,3),pieces.stream().map(Piece::clockwiseTurns).toList());
    }

    @Test void rejectsGapsAndRepeatedCells() {
        assertThrows(IllegalArgumentException.class,()->ConsoleBankLayout.resolve(
                List.of(new Cell(0,0),new Cell(2,0)),false,true));
        assertThrows(IllegalArgumentException.class,()->ConsoleBankLayout.resolve(
                List.of(new Cell(0,0),new Cell(1,0),new Cell(0,0)),false,true));
    }

    private static Cell rotate(Cell c,int turns) {
        for(int i=0;i<turns;i++) c=new Cell(-c.y(),c.x());
        return c;
    }
    private static boolean hasPort(Piece p,int direction) {
        int local=Math.floorMod(direction-p.clockwiseTurns(),4);
        if(p.id().contains("corner")) return local==0||local==1;
        if(p.id().contains("end-")) return local==3;
        return local==0||local==2;
    }
}
