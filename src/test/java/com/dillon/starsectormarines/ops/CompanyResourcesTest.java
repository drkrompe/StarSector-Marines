package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CompanyResourcesTest {
    @Test void billsShowOnlyOrderCostAndPositiveShortfall() {
        var shortRow = CompanyResources.cost("metals", "Metals", "", 41, 60);
        assertEquals("Cost 60", shortRow.markupProperty("cost"));
        assertEquals("Short by 19", shortRow.markupProperty("shortage"));
        assertEquals("company-resource-cost short", shortRow.markupProperty("classes"));
        for (long available : List.of(60L, 15_000_000L, (long) Integer.MAX_VALUE)) {
            var row = CompanyResources.cost("metals", "Metals", "", available, 60);
            assertEquals("Cost 60", row.markupProperty("cost"));
            assertEquals("", row.markupProperty("shortage"));
        }
        assertEquals("", CompanyResources.creditBill(0, 10));
        assertEquals("Credits · Cost 60 · Short by 19", CompanyResources.creditBill(60, 41));
    }

    @Test void stockIsExactAndGroupedRatherThanRepeatedInTheBill() {
        var row = CompanyResources.stock("metals", "Metals", "", 15_000_000);
        assertEquals("15,000,000", row.markupProperty("stock"));
        assertEquals("2,147,483,647", CompanyResources.number(Integer.MAX_VALUE));
        assertEquals(0, CompanyResources.cost("x", "x", "", -1, -1).shortfall());
    }

    @Test void sevenLargeBalancesStayInOneSharedStripAndUpdateReactively() throws Exception {
        var reactor = new Reactor();
        var stock = reactor.signal(IntStream.range(0, 7).mapToObj(i ->
                CompanyResources.stock("stock-" + i, "Heavy machinery", "", 15_000_000)).toList());
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/company/company-resource-stock.mlx"));
        try (var markup = loader.reloadAndBuild(reactor, "company-resource-stock",
                Map.of("stockId", "stock", "rows", stock))) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            document.layout(1920, 1080);
            float previousRight = 0;
            for (int i = 0; i < 7; i++) {
                var label = markup.requireElement("stock-" + i + ":label");
                var box = label.box().borderBox();
                assertEquals("15,000,000", label.text());
                assertTrue(box.width() >= 150, "Exact large balance has insufficient room");
                assertTrue(box.x() >= previousRight && box.right() <= 1920);
                previousRight = box.right();
            }
            stock.set(List.of(CompanyResources.stock("stock-0", "Heavy machinery", "", 14_999_970)));
            markup.flush();
            assertEquals("14,999,970", markup.requireElement("stock-0:label").text());
        }
    }
}
