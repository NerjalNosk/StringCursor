package io.github.nerjalnosk.stringcursor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@Execution(value = ExecutionMode.SAME_THREAD, reason = "non-synchronous implementation")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(StringCursorMethodOrderer.class)
class SimpleStringCursorTest {
    StringCursor cursor= new SimpleStringCursor();
    static final Logger LOGGER = LoggerFactory.getLogger(SimpleStringCursorTest.class);

    @Test
    void test00_init() {
        // This is literally useless, only to split the initialisation time and the first test's actual run time.
    }

    @Test
    void test01_testGetters() {
        LOGGER.debug(() -> String.format("01- %d %d", cursor.getSize(), cursor.getCursor()));
        assertEquals(0, cursor.getCursor());
        assertEquals(0, cursor.getDeletion());
        assertEquals(0, cursor.getHistorySize());
        assertEquals(0, cursor.getHistoryCanceledSize());
        assertEquals(0, cursor.getSelectionSize());
        assertEquals(0, cursor.getSize());
        assertEquals(-1, cursor.getSelectionStart());
        assertEquals("", cursor.getSelectedText());
        assertEquals(new StringCursor.Pair(0, 0), cursor.getSelectionPair());
    }

    @Test
    void test02_testErasers() {
        LOGGER.debug(() -> String.format("02- %d %d", cursor.getSize(), cursor.getCursor()));
        assertFalse(cursor.erase());
        assertEquals(0, cursor.eraseWord());
        assertFalse(cursor.delete());
        assertEquals(0, cursor.deleteWord());
    }

    @Test
    void test03_testWrite() {
        LOGGER.debug(() -> String.format("03-1- %d %d", cursor.getSize(), cursor.getCursor()));
        cursor.write('a');
        assertEquals(1, cursor.getCursor());
        assertEquals(1, cursor.getSize());
        assertEquals(new StringCursor.Pair(1, 1), cursor.getSelectionPair());
        assertEquals("a", cursor.toString());
        LOGGER.debug(() -> String.format("03-2- %d %d", cursor.getSize(), cursor.getCursor()));
    }

    @Test
    void test04_testMove() {
        LOGGER.debug(() -> String.format("04- %d %d", cursor.getSize(), cursor.getCursor()));
        assertEquals(1, cursor.moveRight());
        assertEquals(0, cursor.moveLeft());
        assertEquals(0, cursor.moveLeft());
        assertEquals(1, cursor.moveRight());
        assertEquals(1, cursor.goToWordEnd());
        assertEquals(0, cursor.goToWordStart());
        assertEquals(0, cursor.goToStart());
        assertEquals(1, cursor.goToEnd());
    }

    @Test
    void test05_testHistory() {
        LOGGER.debug(() -> String.format("05- %d %d", cursor.getSize(), cursor.getCursor()));
        assertEquals(1, cursor.getHistorySize());
        cursor.write(' ').write(' ').write('a');
        assertEquals(1, cursor.getHistorySize());
        cursor.write(' ');
        assertEquals(2, cursor.getHistorySize());
        assertEquals(0, cursor.getHistoryCanceledSize());
        cursor.stash();
        assertEquals(3, cursor.getHistorySize());
        assertEquals(0, cursor.getHistoryCanceledSize());
        assertEquals("a  a ", cursor.toString());
        cursor.cancel();
        assertEquals(2, cursor.getHistorySize());
        assertEquals(1, cursor.getHistoryCanceledSize());
        assertEquals("a  a", cursor.toString());
        cursor.cancel();
        assertEquals(1, cursor.getHistorySize());
        assertEquals(2, cursor.getHistoryCanceledSize());
        assertEquals("a", cursor.toString());
        cursor.redo();
        assertEquals(2, cursor.getHistorySize());
        assertEquals(1, cursor.getHistoryCanceledSize());
        assertEquals("a  a", cursor.toString());
        assertEquals("  a", cursor.getSelectedText());
        cursor.moveRight();
        cursor.write(' ');
        assertEquals(2, cursor.getHistorySize());
        assertEquals(0, cursor.getHistoryCanceledSize());
        assertEquals("a  a ", cursor.toString());
    }

    @Test
    void test06_testSelect() {
        LOGGER.debug(() -> String.format("06- %d %d", cursor.getSize(), cursor.getCursor()));
        cursor.selectLeft();
        assertEquals(1, cursor.getSelectionSize());
        assertEquals(4, cursor.getCursor());
        assertTrue(cursor.hasSelection());
        cursor.selectRight();
        assertEquals(0, cursor.getSelectionSize());
        assertEquals(5, cursor.getCursor());
        assertFalse(cursor.hasSelection());
        cursor.selectWordStart();
        assertEquals(2, cursor.getSelectionSize());
        assertEquals("a ", cursor.getSelectedText());
        assertEquals(3, cursor.getCursor());
        assertEquals(new StringCursor.Pair(3, 5), cursor.getSelectionPair());
        cursor.selectTo(0);
        assertEquals(0, cursor.getCursor());
        assertEquals(5, cursor.getSize());
        assertEquals("a  a ", cursor.getSelectedText());
        assertEquals(new StringCursor.Pair(0, 5), cursor.getSelectionPair());
        cursor.moveRight();
        assertEquals(5, cursor.getCursor());
        assertTrue(cursor.getSelectedText().isEmpty());
    }

    @Test
    void test07_testReplace() {
        LOGGER.debug(() -> String.format("07- %d %d", cursor.getSize(), cursor.getCursor()));
        cursor.goToStart();
        cursor.selectTo(cursor.getSize());
        assertTrue(cursor.hasSelection());
        assertEquals(5, cursor.getSelectionSize());
        cursor.write(' ');
        assertEquals(1, cursor.getCursor());
        assertEquals(1, cursor.getSize());
        assertEquals(" ", cursor.toString());
        assertEquals(3, cursor.getHistorySize());
        cursor.cancel();
        assertEquals(1, cursor.getHistoryCanceledSize());
        assertEquals(5, cursor.getSize());
        assertEquals(5, cursor.getSelectionSize());
        assertEquals(5, cursor.getCursor());
        assertEquals("a  a ", cursor.getSelectedText());
        cursor.write("a  a ");
        assertEquals(5, cursor.getHistorySize());
        assertEquals(5, cursor.selectAll());
    }
}
