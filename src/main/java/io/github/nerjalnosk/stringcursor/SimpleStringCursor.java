package io.github.nerjalnosk.stringcursor;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

/**
 * Simple non-synchronous implementation of the
 * {@link StringCursor} interface.
 * <p>
 * Automatically flushes modifications to the
 * history upon any navigation, edit type change,
 * selection or word break character input.
 * <p>
 * System clipboard functions rely on AWT clipboard
 * support.
 */
@SuppressWarnings("unused")
public class SimpleStringCursor implements StringCursor {
    private final Deque<Edit> history = new LinkedList<>();
    private final Deque<Edit> canceled = new LinkedList<>();
    private boolean eraseDel = false;
    private boolean replace = false;
    private boolean select = false;
    private boolean word = false;
    private int cursor;
    private int deletion;
    private int selectStart;
    private int size;
    private String bakedResult = "";
    private String replaced = null;
    private StringBuilder currentEdit = new StringBuilder();

    @Override
    public void cancel() {
        this.stash();
        if (this.history.isEmpty()) return;
        Edit edit = this.history.removeLast();
        this.canceled.addLast(edit);
        this.select = false;
        int len = edit.value().length();
        if (edit.type() == EditType.WRITE) {
            this.cursor = edit.from();
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + this.bakedResult.substring(edit.to());
            this.size -= len;
        } else if (edit.type() == EditType.DELETE) {
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + edit.value() + this.bakedResult.substring(edit.from());
            this.cursor = edit.to();
            this.size += len;
        } else {
            String next = ((edit.from() + len) == this.size) ? "" : this.bakedResult.substring(edit.from() + len);
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + edit.old() + next;
            this.select = true;
            this.selectStart = edit.from();
            this.cursor = edit.from() + edit.old().length();
            this.size = this.size - len + edit.old().length();
        }
    }

    @Override
    public boolean copySystemClipboard() {
        this.stash();
        if (!this.select) return false;
        try {
            StringSelection selection = new StringSelection(this.getSelectedText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean cutSystemClipboard() {
        this.stash();
        if (!this.select) return false;
        try {
            StringSelection selection = new StringSelection(this.getSelectedText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            this.delete();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean delete() {
        this.stashInput();
        if (this.deletion > 0 && !this.eraseDel) this.stashDeletion();
        if (this.select) {
            this.stashDeletion();
            Pair selection = this.getSelectionPair();
            this.selectStart = selection.from();
            this.cursor = selection.to();
            this.deletion = selection.to() - selection.from();
            this.bakedResult = this.bakedResult.substring(0, selection.to()) + this.bakedResult.substring(selection.from());
            this.stashDeletion();
        } else if (this.cursor < this.size) {
            this.eraseDel = true;
            this.deletion++;
            this.size--;
        } else {
            return false;
        }
        this.canceled.clear();
        return true;
    }

    @Override
    public int deleteWord() {
        if (this.deletion > 0) this.stashDeletion();
        if (this.select) {
            int i = this.getSelectionSize();
            this.delete();
            return i;
        }
        this.stashInput();
        this.eraseDel = true;
        boolean b = false;
        int i = 0;
        while (this.cursor + this.deletion < this.size) {
            this.deletion++;
            char c = this.bakedResult.charAt(this.cursor + this.deletion);
            boolean b1 = StringCursor.isWordBreakChar(c);
            if (b && b1) {
                this.deletion--;
                break;
            } else if (!b1) {
                b = true;
            }
            i++;
        }
        this.stashDeletion();
        return i;
    }

    @Override
    public boolean erase() {
        if (this.eraseDel) this.stashDeletion();
        this.stashInput();
        if (this.select) {
            this.stashDeletion();
            Pair selection = this.getSelectionPair();
            this.selectStart = selection.from();
            this.cursor = selection.to();
            this.deletion = selection.to() - selection.from();
            this.stashDeletion();
        } else if (this.cursor > 0) {
            this.deletion++;
            this.cursor--;
            this.size--;
        } else {
            return false;
        }
        this.canceled.clear();
        return true;
    }

    @Override
    public int eraseWord() {
        if (this.eraseDel) this.stashDeletion();
        if (this.select) {
            int i = this.getSelectionSize();
            this.erase();
            return i;
        }
        this.stashInput();
        if (this.deletion > 0) this.stashDeletion();
        boolean b = false;
        int i = 0;
        while (this.cursor - this.deletion > 0) {
            this.deletion++;
            char c = this.bakedResult.charAt(this.cursor - this.deletion);
            boolean b1 = StringCursor.isWordBreakChar(c);
            if (b && b1) {
                this.deletion--;
                break;
            } else if (!b1) {
                b = true;
            }
            i++;
        }
        this.stashDeletion();
        return i;
    }

    @Override
    public Optional<Character> getCharacterAfter() {
        if (this.cursor == this.size) return Optional.empty();
        return Optional.of(this.bakedResult.charAt(this.cursor));
    }

    @Override
    public Optional<Character> getCharacterBefore() {
        if (this.cursor == 0) return Optional.empty();
        return Optional.of(this.bakedResult.charAt(this.cursor-1));
    }

    @Override
    public int getCursor() {
        return cursor;
    }

    @Override
    public int getDeletion() {
        return deletion;
    }

    @Override
    public int getHistorySize() {
        return this.history.size();
    }

    @Override
    public int getHistoryCanceledSize() {
        return this.canceled.size();
    }

    @Override
    public String getSelectedText() {
        if (!this.select) return "";
        Pair selection = this.getSelectionPair();
        return this.bakedResult.substring(selection.from(), selection.to());
    }

    @Override
    public Pair getSelectionPair() {
        if (!this.select) {
            return new Pair(this.cursor, this.cursor);
        }
        return new Pair(this.cursor, this.selectStart);
    }

    @Override
    public int getSelectionSize() {
        if (!this.select) return 0;
        return Math.abs(this.cursor - this.selectStart);
    }

    @Override
    public int getSelectionStart() {
        if (!this.select) return -1;
        return this.selectStart;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public void goTo(int pos) {
        this.stash();
        this.select = false;
        this.cursor = Math.min(Math.max(pos, 0), this.size);
    }

    @Override
    public int goToEnd() {
        this.stash();
        this.select = false;
        this.cursor = this.size;
        return this.cursor;
    }

    @Override
    public int goToStart() {
        this.stash();
        this.select = false;
        this.cursor = 0;
        return 0;
    }

    @Override
    public int goToWordEnd() {
        this.stash();
        navigateToWordEnd();
        this.select = false;
        return this.cursor;
    }

    @Override
    public int goToWordStart() {
        this.stash();
        navigateToWordStart();
        this.select = false;
        return this.cursor;
    }

    @Override
    public boolean hasSelection() {
        return this.select;
    }

    @Override
    public void insert(String input) {
        this.stash();
        this.currentEdit.append(input);
        this.stashInput();
    }

    @Override
    public void move(int jump) {
        this.stash();
        this.select = false;
        this.cursor = Math.min(Math.max(this.cursor + jump, 0), this.size);
    }

    @Override
    public int moveLeft() {
        this.stash();
        if (this.select) {
            this.select = false;
            this.cursor = this.getSelectionPair().from();
        } else if (this.cursor > 0) {
            this.cursor--;
        }
        return this.cursor;
    }

    @Override
    public int moveRight() {
        this.stash();
        if (this.select) {
            this.cursor = this.getSelectionPair().to();
            this.select = false;
        } else if (this.cursor < this.size) {
            this.cursor++;
        }
        return this.cursor;
    }

    @Override
    public boolean pasteSystemClipboard() {
        this.stash();
        if (Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            try {
                String input = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                this.currentEdit.append(input);
                this.bakedResult = this.bakedResult.substring(0, this.cursor) + input + this.bakedResult.substring(this.cursor);
                this.size += input.length();
                this.cursor += input.length();
                this.stashInput();
                return true;
            } catch (Exception ex) {
                // oh, well
            }
        }
        return false;
    }

    @Override
    public boolean redo() {
        if (this.canceled.isEmpty()) return false;
        this.stash();
        Edit edit = this.canceled.removeLast();
        this.history.addLast(edit);
        if (edit.type() == EditType.WRITE) {
            String value = edit.value();
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + value + this.bakedResult.substring(edit.from());
            this.selectStart = edit.from();
            this.cursor = edit.to();
            this.select = true;
            this.size += value.length();
        } else if (edit.type() == EditType.DELETE) {
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + this.bakedResult.substring(edit.to());
            this.select = false;
            this.cursor = edit.from();
            this.size -= edit.value().length();
        } else {
            int len = edit.value().length();
            this.bakedResult = this.bakedResult.substring(0, edit.from()) + edit.old() + this.bakedResult.substring(edit.from() + len);
            this.select = true;
            this.selectStart = edit.from();
            this.cursor = edit.from() + len;
            this.size = this.size - edit.value().length() + len;
        }
        return true;
    }

    @Override
    public int selectLeft() {
        this.stash();
        if (this.cursor == 0) {
            if (!this.select) return 0;
            return this.selectStart;
        }
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor--;
        if (this.cursor == this.selectStart) {
            this.select = false;
            return 0;
        }
        return Math.abs(this.selectStart - this.cursor);
    }

    @Override
    public int selectRight() {
        this.stash();
        if (this.cursor == this.size) {
            if (!this.select) return 0;
            return this.size - this.selectStart;
        }
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor++;
        if (this.cursor == this.selectStart) {
            this.select = false;
            return 0;
        }
        return Math.abs(this.selectStart - this.cursor);
    }

    @Override
    public int selectTo(int pos) {
        this.stash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor = Math.min(Math.max(pos, 0), this.size);
        if (this.cursor == this.selectStart) {
            this.select = false;
            return 0;
        }
        return Math.abs(this.selectStart - this.cursor);
    }

    @Override
    public int selectWordEnd() {
        this.stash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        navigateToWordEnd();
        if (this.selectStart == this.cursor) {
            this.select = false;
            return 0;
        }
        return Math.abs(this.cursor - this.selectStart);
    }

    @Override
    public int selectWordStart() {
        this.stash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        navigateToWordStart();
        if (this.selectStart == this.cursor) {
            this.select = false;
            return 0;
        }
        return Math.abs(this.selectStart - this.cursor);
    }

    @Override
    public void stash() {
        this.stashInput();
        this.stashDeletion();
    }

    @Override
    public void stashDeletion() {
        int del = Math.min(Math.max(this.deletion, 0), this.cursor);
        if (del == 0) {
            return;
        }
        this.deletion = 0;
        if (this.eraseDel) {
            int target = this.cursor + del;
            String deleted = this.bakedResult.substring(this.cursor, target);
            this.history.addLast(new Edit(EditType.DELETE, target, this.cursor, deleted, null));
            this.size = this.size - target + this.cursor;
            this.eraseDel = false;
        } else {
            int newCursor = this.cursor - del;
            String deleted = this.bakedResult.substring(newCursor, this.cursor);
            this.history.addLast(new Edit(EditType.DELETE, newCursor, this.cursor, deleted, null));
            this.cursor = newCursor;
        }
        this.size -= del;
        this.canceled.clear();
    }

    @Override
    public void stashInput() {
        if (this.currentEdit.isEmpty()) {
            return;
        }
        String input = this.currentEdit.toString();
        this.currentEdit = new StringBuilder();
        if (this.replace) {
            this.history.addLast(new Edit(EditType.REPLACE, this.cursor - input.length(), this.cursor, input, this.replaced));
            this.replaced = null;
            this.replace = false;
        } else {
            this.history.addLast(new Edit(EditType.WRITE, this.cursor - input.length(), this.cursor, input, null));
        }
        this.word = false;
        this.canceled.clear();
    }

    @Override
    public String toString() {
        return this.bakedResult;
    }

    @Override
    public StringCursor write(char c) {
        this.stashDeletion();
        this.canceled.clear();
        if (this.select) {
            Pair pair = this.getSelectionPair();
            this.replace = true;
            this.select = false;
            this.replaced = this.bakedResult.substring(pair.from(), pair.to());
            this.bakedResult = this.bakedResult.substring(0, pair.from()) + c + this.bakedResult.substring(pair.to());
            this.cursor = pair.from()+1;
            this.word = !StringCursor.isWordBreakChar(c);
            this.size = pair.from() + 1 + this.size - pair.to();
            this.currentEdit.append(c);
            return this;
        }
        if (StringCursor.isWordBreakChar(c)) {
            if (this.word) this.stashInput();
        } else {
            this.word = true;
        }
        int pos = this.cursor++;
        this.bakedResult = this.bakedResult.substring(0, pos) + c + this.bakedResult.substring(pos);
        this.size++;
        this.currentEdit.append(c);
        return this;
    }

    private void navigateToWordEnd() {
        boolean b = false;
        while (this.cursor < this.size) {
            this.cursor++;
            char c = this.bakedResult.charAt(this.cursor);
            boolean b1 = StringCursor.isWordBreakChar(c);
            if (b && b1) {
                this.cursor--;
                break;
            } else if (!b1) {
                b = true;
            }
        }
    }

    private void navigateToWordStart() {
        boolean b = false;
        while (this.cursor > 0) {
            this.cursor--;
            char c = this.bakedResult.charAt(this.cursor);
            boolean b1 = StringCursor.isWordBreakChar(c);
            if (b && b1) {
                this.cursor++;
                break;
            } else if (!b1) {
                b = true;
            }
        }
    }
}
