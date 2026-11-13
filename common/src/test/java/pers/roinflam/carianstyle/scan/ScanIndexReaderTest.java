package pers.roinflam.carianstyle.scan;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 读清单：合法的读得对，缺文件、空清单、首行不对、行格式不对、没排序都抛异常。 */
class ScanIndexReaderTest {

    private static final String H = ScanIndex.HEADER;

    private static List<String> lines(String text) throws IOException {
        return ScanIndex.readLines(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertRejectsEnchantments(String text, String messagePart) throws IOException {
        List<String> l = lines(text);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndex.parseEnchantments("t", l));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    private static void assertRejectsSubscribers(String text, String messagePart) throws IOException {
        List<String> l = lines(text);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndex.parseSubscribers("t", l));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
    }

    @Test
    void readsEnchantments() throws IOException {
        List<String> got = ScanIndex.parseEnchantments("t", lines(H + "\na.B\na.B$C\na.Ba\n"));
        assertEquals(List.of("a.B", "a.B$C", "a.Ba"), got);
    }

    @Test
    void readsSubscribers() throws IOException {
        List<ScanIndex.Subscriber> got = ScanIndex.parseSubscribers("t", lines(H + "\nCLIENT\ta.A\nBOTH\ta.B$1\nBOTH\ta.C\n"));
        assertEquals(List.of(
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, "a.A"),
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "a.B$1"),
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "a.C")), got);
    }

    @Test
    void acceptsCrlf() throws IOException {
        assertEquals(List.of("a.B", "a.C"), ScanIndex.parseEnchantments("t", lines(H + "\r\na.B\r\na.C\r\n")));
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        assertRejectsEnchantments("", "空文件");
        assertRejectsSubscribers("", "空文件");
    }

    @Test
    void rejectsHeaderOnly() throws IOException {
        assertRejectsEnchantments(H + "\n", "一个类都没有");
        assertRejectsSubscribers(H + "\n", "一个类都没有");
    }

    @Test
    void rejectsWrongHeader() throws IOException {
        assertRejectsEnchantments("# carianstyle scan index v2\na.B\n", "首行");
        assertRejectsEnchantments("a.B\na.C\n", "首行");
        assertRejectsSubscribers("BOTH\ta.B\n", "首行");
    }

    @Test
    void rejectsBlankLine() throws IOException {
        assertRejectsEnchantments(H + "\na.B\n\na.C\n", "空行");
    }

    @Test
    void rejectsUnsortedAndDuplicate() throws IOException {
        assertRejectsEnchantments(H + "\na.C\na.B\n", "没有按类名排序");
        assertRejectsEnchantments(H + "\na.B\na.B\n", "重复");
        assertRejectsSubscribers(H + "\nBOTH\ta.C\nCLIENT\ta.B\n", "没有按类名排序");
        assertRejectsSubscribers(H + "\nBOTH\ta.B\nCLIENT\ta.B\n", "重复");
    }

    @Test
    void rejectsBadNames() throws IOException {
        assertRejectsEnchantments(H + "\na/B\n", "不是合法的类名");
        assertRejectsEnchantments(H + "\na.B \n", "不是合法的类名");
        assertRejectsEnchantments(H + "\nBOTH\ta.B\n", "不是合法的类名");
    }

    @Test
    void rejectsMalformedSubscriberLines() throws IOException {
        assertRejectsSubscribers(H + "\na.B\n", "端<TAB>类名");
        assertRejectsSubscribers(H + "\nBOTH a.B\n", "端<TAB>类名");
        assertRejectsSubscribers(H + "\nBOTH\ta.B\textra\n", "端<TAB>类名");
        assertRejectsSubscribers(H + "\nSERVER\ta.B\n", "BOTH 或 CLIENT");
        assertRejectsSubscribers(H + "\nboth\ta.B\n", "BOTH 或 CLIENT");
        assertRejectsSubscribers(H + "\nBOTH\t\n", "不是合法的类名");
    }

    @Test
    void missingResourceThrows() {
        // 单测的类路径上没有生成的清单，正好验「缺文件就抛异常」
        ScanIndex.resetForTest();
        IllegalStateException e = assertThrows(IllegalStateException.class, ScanIndex::enchantmentClasses);
        assertTrue(e.getMessage().contains("找不到类清单"), e.getMessage());
        e = assertThrows(IllegalStateException.class, ScanIndex::subscribers);
        assertTrue(e.getMessage().contains("找不到类清单"), e.getMessage());
    }

    @Test
    void renderedTextRoundTrips() throws IOException {
        List<String> ench = List.of("z.A", "a.B$C", "a.B", "a.B");
        String text = ScanIndex.renderEnchantments(ench);
        assertEquals(H + "\na.B\na.B$C\nz.A\n", text);
        assertEquals(List.of("a.B", "a.B$C", "z.A"), ScanIndex.parseEnchantments("t", lines(text)));

        List<ScanIndex.Subscriber> subs = List.of(
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "b.X"),
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, "a.Y"));
        String subText = ScanIndex.renderSubscribers(subs);
        assertEquals(H + "\nCLIENT\ta.Y\nBOTH\tb.X\n", subText);
        assertEquals(List.of(subs.get(1), subs.get(0)), ScanIndex.parseSubscribers("t", lines(subText)));
    }
}
