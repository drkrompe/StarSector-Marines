package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayRulesCsvTest {

    @Test
    void quotedCellsKeepCommasLineBreaksAndDoubledQuotes() {
        String csv = "id,trigger,conditions,script,text,options,notes\r\n"
                + "a,DialogOptionSelected,\"$option == x score:2\r\nCmd intercept\",Cmd fork,\"He said \"\"go\"\", then left\",,note\r\n";

        List<PlayRulesCsv.Row> rows = PlayRulesCsv.parse(csv);

        assertEquals(1, rows.size());
        PlayRulesCsv.Row row = rows.get(0);
        assertEquals("a", row.id());
        assertEquals("DialogOptionSelected", row.trigger());
        assertEquals("$option == x score:2\nCmd intercept", row.conditions());
        assertEquals("Cmd fork", row.script());
        assertEquals("He said \"go\", then left", row.text());
        assertEquals("", row.options());
    }

    @Test
    void commentAndBlankRowsAreSkipped() {
        String csv = "id,trigger\n#note,x\n,,\nb,PopulateOptions,,,,\"20:o:Go\"";

        List<PlayRulesCsv.Row> rows = PlayRulesCsv.parse(csv);

        assertEquals(1, rows.size());
        assertEquals("b", rows.get(0).id());
        assertEquals("20:o:Go", rows.get(0).options());
    }
}
