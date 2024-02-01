package io.github.nerjalnosk.stringcursor;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Backend implementation of a cursored text input,
 * mirroring UI text input fields.
 * <p>
 * Supports both canceling and un-canceling
 * operations, among many other functionalities.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface StringCursor {

    /**
     * Cancels the last executed modification,
     * according to the current implementation's
     * modification application
     */
    void cancel();

    /**
     * Copies the current selection to the system
     * clipboard, if supported, and returns whether
     * copy is considered successful (may depend on
     * systems and implementations)
     * @return Whether the copy is considered
     *         successful.
     */
    boolean copySystemClipboard();

    /**
     * Cuts the current selection to the system
     * clipboard, if supported, and returns whether
     * copy is considered successful (may depend on
     * systems and implementations).
     * <p>
     * Selected text is only deleted if cut is deemed
     * successful.
     * @return Whether the copy is considered
     *         successful.
     */
    boolean cutSystemClipboard();

    /**
     * Deletes the first character after the cursor,
     * if any, and returns whether a character was
     * effectively removed.
     * @return Whether a character was effectively
     *         removed.
     */
    boolean delete();

    /**
     * Deletes to the first end of word after the
     * cursor, or to the end if none until then,
     * and returns how many characters were
     * effectively removed.
     * @return How many characters were effectively
     *         removed.
     */
    int deleteWord();

    /**
     * Removes the last character before the
     * cursor, if any, and returns whether a
     * character was effectively removed.
     * @return Whether a character was effectively
     *         removed.
     */
    boolean erase();

    /**
     * Removes to the last start of word before the
     * cursor, or to the beginning if none between
     * then, and returns how many characters were
     * effectively removed.
     * @return How many characters were effectively
     *         removed.
     */
    int eraseWord();

    /**
     * Returns the character right after the current
     * cursor position, or {@link Optional#EMPTY} if
     * none.
     * @return The character right after the
     *         current cursor position, or
     *         {@link Optional#EMPTY} if none.
     */
    Optional<Character> getCharacterAfter();

    /**
     * Returns the character right before the current
     * cursor position, or {@link Optional#EMPTY} if
     * none.
     * @return The character right before the
     *         current cursor position, or
     *         {@link Optional#EMPTY} if none.
     */
    Optional<Character> getCharacterBefore();

    /**
     * Returns the current position of the cursor.
     * @return The current position of the cursor.
     */
    int getCursor();

    /**
     * Returns how many characters are queued for
     * deletion. (removed but not yet registered in
     * the history)
     * @return How many characters are queued for
     *         deletion.
     */
    int getDeletion();

    /**
     * Returns how many operations are stored in
     * the history.
     * @return How many operations are stored in
     *         the history.
     */
    int getHistorySize();

    /**
     * Returns how many operations are stored in
     * the canceled history. (that can be reapplied,
     * provided no other modification is executed
     * before then)
     * @return How many operations are stored in
     *         the canceled history.
     */
    int getHistoryCanceledSize();

    /**
     * Returns the currently selected text, or an
     * empty string if none.
     * @return The currently selected text.
     */
    String getSelectedText();

    /**
     * Returns the current selection index range.
     * Will be a pair of the same number twice if
     * nothing is selected.
     * @return The current selection index range.
     */
    Pair getSelectionPair();

    /**
     * Returns how many characters are currently
     * selected.
     * @return How many characters are currently
     *         selected.
     */
    int getSelectionSize();

    /**
     * Returns the current index of selection start.
     * Returns {@code -1} if nothing is selected.
     * <p>
     * Might be either superior or inferior to the
     * cursor position, depending on where the cursor
     * moved since the beginning of the selection.
     * @return The current index of selection start.
     */
    int getSelectionStart();

    /**
     * Returns the size of the built-up string.
     * @return The size of the built-up string.
     */
    int getSize();

    /**
     * Moves the cursor to the provided position.
     * @param pos The position to move the cursor to.
     * @throws IndexOutOfBoundsException If the
     *         specified index is out of the current
     *         string's bounds, if the current
     *         implementation doesn't support out of
     *         bounds specifications.
     */
    void goTo(int pos) throws IndexOutOfBoundsException;

    /**
     * Moves the cursor to the end of the built-up string.
     * @return The position of the cursor after moving.
     */
    int goToEnd();

    /**
     * Moves the cursor to the beginning of the built-up
     * string.
     * @return The position of the cursor after moving.
     */
    int goToStart();

    /**
     * Moves the cursor to the first word end after the
     * current cursor position, or the current end if
     * none until then.
     * @return The position of the cursor after moving.
     */
    int goToWordEnd();

    /**
     * Moves the cursor to the last beginning of word
     * before the current cursor position, or the
     * start if none between then.
     * @return The position of the cursor after moving.
     */
    int goToWordStart();

    /**
     * Returns whether any text is selected.
     * @return whether any text is selected.
     */
    boolean hasSelection();

    /**
     * Inserts the provided string at the current cursor
     * position. Will possibly be registered as one or
     * multiple history entries, depending on the
     * implementation.
     * @param input The string to insert at the cursor's
     *              position.
     */
    void insert(String input);

    /**
     * Moves the cursor by the provided offset.
     * A positive offset will move towards the end,
     * while a negative offset will move towards the
     * beginning.
     * @param jump The offset to apply to the cursor.
     * @throws IndexOutOfBoundsException If the provided
     *         offset reaches beyond the current text's
     *         bounds, if the current implementation
     *         does not support out of bounds
     *         specifications.
     */
    void move(int jump) throws IndexOutOfBoundsException;

    /**
     * Moves the cursor one character to the left
     * (towards the beginning), and returns the
     * outing position of the cursor.
     * @return The position of the cursor after moving.
     */
    int moveLeft();

    /**
     * Moves the cursor one character to the right
     * (towards the end), and returns the outing
     * position of the cursor.
     * @return The position of the cursor after moving.
     */
    int moveRight();

    /**
     * Inserts the system clipboard text value at the
     * current cursor position, if any and supported,
     * and returns whether anything was inserted.
     * @return Whether anything was inserted from the
     *         system clipboard.
     */
    boolean pasteSystemClipboard();

    /**
     * Executes the last canceled operation (the latest
     * from the canceled history) if any, and returns
     * whether there was one to be un-canceled.
     * @return Whether an operation could be
     *         un-canceled.
     */
    boolean redo();

    /**
     * Selects everything from beginning to end, and
     * returns how many characters were selected,
     * theoretically equals to the current text size.
     * @return How many characters were selected.
     */
    default int selectAll() {
        this.goToStart();
        this.selectTo(this.getSize());
        return this.getSelectionSize();
    }

    /**
     * Expends or reduces the selection by one character
     * to the left (towards the beginning), and returns
     * the size of the selection afterward.
     * @return The size of the selection.
     */
    int selectLeft();

    /**
     * Expands or reduces the selection by one character
     * to the right (towards the end), and returns the
     * size of the selection afterward.
     * @return The size of the selection.
     */
    int selectRight();

    /**
     * Expands or reduces the selection to the specified
     * position and returns the resulting selection
     * size.
     * @param pos The position to which expand or
     *            reduce the selection.
     * @return The resulting selection size.
     */
    int selectTo(int pos);

    /**
     * Expands or reduces the selection to the first
     * word end after the current cursor position, or
     * the current if none before then, and returns the
     * outing selection size.
     * @return The resulting selection size.
     */
    int selectWordEnd();

    /**
     * Expands or reduces the selection to the latest
     * word start before the current cursor position, or
     * the beginning if none between then, and returns
     * the outing selection size.
     * @return The resulting selection size.
     */
    int selectWordStart();

    /**
     * Flushes all pending modifications in the history.
     */
    void stash();

    /**
     * Flushes all pending removal modifications in the
     * history.
     */
    void stashDeletion();

    /**
     * Flushes all pending input modifications in the
     * history.
     */
    void stashInput();

    /**
     * Writes the provided input character at the
     * cursor's position and returns {@code this}.
     * @param c The character to input.
     * @return {@code this}.
     */
    StringCursor write(char c);

    /**
     * Writes the provided string at the cursor's
     * position and returns {@code this}.
     * @param input The character to input.
     * @return {@code this}
     */
    default StringCursor write(String input) {
        StringCursor cursor = this;
        for (char c : input.toCharArray()) {
            cursor = cursor.write(c);
        }
        return cursor;
    }

    /**
     * Returns whether the specified character is a
     * word break character.
     * <p>
     * Will return {@code true} for both whitespace and
     * punctuation characters.
     * @param c The character to process.
     * @return Whether the specified character is a word
     *         break character.
     */
    static boolean isWordBreakChar(char c) {
        String s = String.valueOf(c);
        return Character.isWhitespace(c) ||  Pattern.matches("[\\p{Punct}\\p{IsPunctuation}]", s);
    }

    /**
     * History edit types.
     */
    enum EditType {
        /** Input edit */
        WRITE,
        /** Selection replacement edit */
        REPLACE,
        /** Removal edit */
        DELETE
    }

    /**
     * History edit record class.
     * @param type The type of operation.
     * @param from The lowest position affected by the
     *             modification.
     * @param to The highest position affected by the
     *           modification.
     * @param value The value of the modification.
     * @param old The old value, overwritten by the
     *            modification. Only relevant for
     *            {@link EditType#REPLACE} edits.
     */
    record Edit(EditType type, int from, int to, String value, String old) {
        @Override
        public String toString() {
            return "Edit{type=" + type + ", from=" + from + ", to=" + to + ", value='" + value + "'" + ", old='" + old + "'}";
        }
    }

    /**
     * A numeral range.
     * @param from The lower value of the range.
     * @param to The higher value of the range.
     */
    record Pair(int from, int to) {
        /**
         * Pair constructor, holds min-max range detection.
         * @param from One of the range's values.
         * @param to The other of the range's values.
         */
        public Pair(int from, int to) {
            if (from > to) {
                this.to = from;
                this.from = to;
            } else {
                this.from = from;
                this.to = to;
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Pair p && p.from() == from && p.to == to;
        }

        @Override
        public String toString() {
            return String.format("[%d-%d]", this.from(), this.to());
        }
    }
}
