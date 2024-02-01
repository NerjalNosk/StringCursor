package io.github.nerjalnosk.stringcursor;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple synchronous implementation of the
 * {@link StringCursor} interface.
 * <p>
 * Automatically flushes modifications to the
 * history upon any navigation, edit type change,
 * selection or word break character input.
 * <p>
 * System clipboard functions rely on AWT clipboard
 * support.
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public class SynchronizedStringCursor implements StringCursor {
    private final Lock lock = new ReentrantLock();
    private final Deque<Edit> history = new LinkedList<>();
    private final Deque<Edit> canceled = new LinkedList<>();
    private boolean eraseDel = false;
    private boolean replace = false;
    private boolean select = false;
    private boolean word = false;
    private int cursor;
    private int deletion = 0;
    private int selectStart = 0;
    private int size;
    private String bakedResult;
    private String replaced = null;
    private StringBuilder currentEdit = new StringBuilder();

    public SynchronizedStringCursor() {
        this.bakedResult = "";
        this.size = 0;
        this.cursor = 0;
    }

    public SynchronizedStringCursor(String initialValue) {
        this.bakedResult = initialValue;
        this.size = initialValue.length();
        this.cursor = initialValue.length();
    }

    protected Pair asyncGetSelectionPair() {
        if (!this.select) {
            return new Pair(this.cursor, this.cursor);
        }
        return new Pair(this.cursor, this.selectStart);
    }

    protected void asyncStash() {
        this.asyncStashInput();
        this.asyncStashDeletion();
    }

    protected void asyncStashDeletion() {
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

    protected void asyncStashInput() {
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
    public void cancel() {
        this.lock.lock();
        this.asyncStash();
        if (this.history.isEmpty()) {
            this.lock.unlock();
            return;
        }
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
        this.lock.unlock();
    }

    @Override
    public boolean copySystemClipboard() {
        this.lock.lock();
        this.asyncStash();
        if (!this.select) {
            this.lock.unlock();
            return false;
        }
        try {
            StringSelection selection = new StringSelection(this.getSelectedText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean cutSystemClipboard() {
        this.lock.lock();
        this.asyncStash();
        if (!this.select) {
            this.lock.unlock();
            return false;
        }
        try {
            StringSelection selection = new StringSelection(this.getSelectedText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            this.delete();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean delete() {
        this.lock.lock();
        this.asyncStashInput();
        if (this.deletion > 0 && !this.eraseDel) this.asyncStashDeletion();
        if (this.select) {
            this.asyncStashDeletion();
            Pair selection = this.asyncGetSelectionPair();
            this.selectStart = selection.from();
            this.cursor = selection.to();
            this.deletion = selection.to() - selection.from();
            this.bakedResult = this.bakedResult.substring(0, selection.to()) + this.bakedResult.substring(selection.from());
            this.asyncStashDeletion();
        } else if (this.cursor < this.size) {
            this.eraseDel = true;
            this.deletion++;
            this.size--;
        } else {
            this.lock.unlock();
            return false;
        }
        this.canceled.clear();
        this.lock.unlock();
        return true;
    }

    @Override
    public int deleteWord() {
        this.lock.lock();
        if (this.deletion > 0) this.asyncStashDeletion();
        if (this.select) {
            int i = this.getSelectionSize();
            this.delete();
            this.lock.unlock();
            return i;
        }
        this.asyncStashInput();
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
        this.asyncStashDeletion();
        this.lock.unlock();
        return i;
    }

    @Override
    public boolean erase() {
        this.lock.lock();
        if (this.eraseDel) this.asyncStashDeletion();
        this.asyncStashInput();
        if (this.select) {
            this.asyncStashDeletion();
            Pair selection = this.asyncGetSelectionPair();
            this.selectStart = selection.from();
            this.cursor = selection.to();
            this.deletion = selection.to() - selection.from();
            this.asyncStashDeletion();
        } else if (this.cursor > 0) {
            this.deletion++;
            this.cursor--;
            this.size--;
        } else {
            this.lock.unlock();
            return false;
        }
        this.canceled.clear();
        this.lock.unlock();
        return true;
    }

    @Override
    public int eraseWord() {
        this.lock.lock();
        if (this.eraseDel) this.asyncStashDeletion();
        if (this.select) {
            int i = this.getSelectionSize();
            this.erase();
            this.lock.unlock();
            return i;
        }
        this.asyncStashInput();
        if (this.deletion > 0) this.asyncStashDeletion();
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
        this.asyncStashDeletion();
        this.lock.unlock();
        return i;
    }

    @Override
    public Optional<Character> getCharacterAfter() {
        this.lock.lock();
        Optional<Character> optional;
        if (this.cursor == this.size) optional = Optional.empty();
        else optional = Optional.of(this.bakedResult.charAt(this.cursor));
        this.lock.unlock();
        return optional;
    }

    @Override
    public Optional<Character> getCharacterBefore() {
        this.lock.lock();
        Optional<Character> optional;
        if (this.cursor == 0) optional = Optional.empty();
        else optional = Optional.of(this.bakedResult.charAt(this.cursor-1));
        this.lock.unlock();
        return optional;
    }

    @Override
    public int getCursor() {
        this.lock.lock();
        int i = cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public int getDeletion() {
        this.lock.lock();
        int i = deletion;
        this.lock.unlock();
        return i;
    }

    @Override
    public int getHistorySize() {
        this.lock.lock();
        int i = this.history.size();
        this.lock.unlock();
        return i;
    }

    @Override
    public int getHistoryCanceledSize() {
        this.lock.lock();
        int i = this.canceled.size();
        this.lock.unlock();
        return i;
    }

    @Override
    public String getSelectedText() {
        this.lock.lock();
        String s;
        if (!this.select) {
            s = "";
        } else {
            Pair selection = this.asyncGetSelectionPair();
            s = this.bakedResult.substring(selection.from(), selection.to());
        }
        this.lock.unlock();
        return s;
    }

    @Override
    public Pair getSelectionPair() {
        this.lock.lock();
        Pair pair;
        if (!this.select) {
            pair = new Pair(this.cursor, this.cursor);
        } else {
            pair =new Pair(this.cursor, this.selectStart);
        }
        this.lock.unlock();
        return pair;
    }

    @Override
    public int getSelectionSize() {
        this.lock.lock();
        int i;
        if (!this.select) i = 0;
        else i = Math.abs(this.cursor - this.selectStart);
        this.lock.unlock();
        return i;
    }

    @Override
    public int getSelectionStart() {
        this.lock.lock();
        int i;
        if (!this.select) i = -1;
        else i = this.selectStart;
        this.lock.unlock();
        return i;
    }

    @Override
    public int getSize() {
        this.lock.lock();
        int i = size;
        this.lock.unlock();
        return i;
    }

    @Override
    public void goTo(int pos) {
        this.lock.lock();
        this.asyncStash();
        this.select = false;
        this.cursor = Math.min(Math.max(pos, 0), this.size);
        this.lock.unlock();
    }

    @Override
    public int goToEnd() {
        this.lock.lock();
        this.asyncStash();
        this.select = false;
        this.cursor = this.size;
        int i = this.cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public int goToStart() {
        this.lock.lock();
        this.asyncStash();
        this.select = false;
        this.cursor = 0;
        this.lock.unlock();
        return 0;
    }

    @Override
    public int goToWordEnd() {
        this.lock.lock();
        this.asyncStash();
        navigateToWordEnd();
        this.select = false;
        int i = this.cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public int goToWordStart() {
        this.lock.lock();
        this.asyncStash();
        navigateToWordStart();
        this.select = false;
        int i = this.cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public boolean hasSelection() {
        this.lock.lock();
        boolean b = this.select;
        this.lock.unlock();
        return b;
    }

    @Override
    public void insert(String input) {
        this.lock.lock();
        this.asyncStash();
        this.currentEdit.append(input);
        this.asyncStashInput();
        this.lock.unlock();
    }

    @Override
    public void move(int jump) {
        this.lock.lock();
        this.asyncStash();
        this.select = false;
        this.cursor = Math.min(Math.max(this.cursor + jump, 0), this.size);
        this.lock.unlock();
    }

    @Override
    public int moveLeft() {
        this.lock.lock();
        this.asyncStash();
        if (this.select) {
            this.select = false;
            this.cursor = this.asyncGetSelectionPair().from();
        } else if (this.cursor > 0) {
            this.cursor--;
        }
        int i = this.cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public int moveRight() {
        this.lock.lock();
        this.asyncStash();
        if (this.select) {
            this.cursor = this.asyncGetSelectionPair().to();
            this.select = false;
        } else if (this.cursor < this.size) {
            this.cursor++;
        }
        int i = this.cursor;
        this.lock.unlock();
        return i;
    }

    @Override
    public boolean pasteSystemClipboard() {
        this.lock.lock();
        this.asyncStash();
        if (Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            try {
                String input = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                this.currentEdit.append(input);
                this.bakedResult = this.bakedResult.substring(0, this.cursor) + input + this.bakedResult.substring(this.cursor);
                this.size += input.length();
                this.cursor += input.length();
                this.asyncStashInput();
                this.lock.unlock();
                return true;
            } catch (Exception ex) {
                // oh, well
            }
        }
        this.lock.unlock();
        return false;
    }

    @Override
    public boolean redo() {
        this.lock.lock();
        if (this.canceled.isEmpty()) {
            this.lock.unlock();
            return false;
        }
        this.asyncStash();
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
        this.lock.unlock();
        return true;
    }

    @Override
    public int selectLeft() {
        this.lock.lock();
        this.asyncStash();
        if (this.cursor == 0) {
            int i;
            if (!this.select) i = 0;
            else i = this.selectStart;
            this.lock.unlock();
            return i;
        }
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor--;
        if (this.cursor == this.selectStart) {
            this.select = false;
            this.lock.unlock();
            return 0;
        }
        int i = Math.abs(this.selectStart - this.cursor);
        this.lock.unlock();
        return i;
    }

    @Override
    public int selectRight() {
        this.lock.lock();
        this.asyncStash();
        if (this.cursor == this.size) {
            int i;
            if (!this.select) i = 0;
            else i = this.size - this.selectStart;
            this.lock.unlock();
            return i;
        }
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor++;
        if (this.cursor == this.selectStart) {
            this.select = false;
            this.lock.unlock();
            return 0;
        }
        int i = Math.abs(this.selectStart - this.cursor);
        this.lock.unlock();
        return i;
    }

    @Override
    public int selectTo(int pos) {
        this.lock.lock();
        this.asyncStash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        this.cursor = Math.min(Math.max(pos, 0), this.size);
        if (this.cursor == this.selectStart) {
            this.select = false;
            this.lock.unlock();
            return 0;
        }
        int i = Math.abs(this.selectStart - this.cursor);
        this.lock.unlock();
        return i;
    }

    @Override
    public int selectWordEnd() {
        this.lock.lock();
        this.asyncStash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        navigateToWordEnd();
        if (this.selectStart == this.cursor) {
            this.select = false;
            this.lock.unlock();
            return 0;
        }
        int i = Math.abs(this.cursor - this.selectStart);
        this.lock.unlock();
        return i;
    }

    @Override
    public int selectWordStart() {
        this.lock.lock();
        this.asyncStash();
        if (!this.select) {
            this.select = true;
            this.selectStart = this.cursor;
        }
        navigateToWordStart();
        if (this.selectStart == this.cursor) {
            this.select = false;
            this.lock.unlock();
            return 0;
        }
        int i = Math.abs(this.selectStart - this.cursor);
        this.lock.unlock();
        return i;
    }

    @Override
    public void stash() {
        this.lock.lock();
        this.asyncStashInput();
        this.asyncStashDeletion();
        this.lock.unlock();
    }

    @Override
    public void stashDeletion() {
        this.lock.lock();
        int del = Math.min(Math.max(this.deletion, 0), this.cursor);
        if (del == 0) {
            this.lock.unlock();
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
        this.lock.unlock();
    }

    @Override
    public void stashInput() {
        this.lock.lock();
        if (this.currentEdit.isEmpty()) {
            this.lock.unlock();
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
        this.lock.unlock();
    }

    @Override
    public String toString() {
        this.lock.lock();
        String s = this.bakedResult;
        this.lock.unlock();
        return s;
    }

    @Override
    public StringCursor write(char c) {
        this.lock.lock();
        this.asyncStashDeletion();
        this.canceled.clear();
        if (this.select) {
            Pair pair = this.asyncGetSelectionPair();
            this.replace = true;
            this.select = false;
            this.replaced = this.bakedResult.substring(pair.from(), pair.to());
            this.bakedResult = this.bakedResult.substring(0, pair.from()) + c + this.bakedResult.substring(pair.to());
            this.cursor = pair.from()+1;
            this.word = !StringCursor.isWordBreakChar(c);
            this.size = pair.from() + 1 + this.size - pair.to();
            this.currentEdit.append(c);
            this.lock.unlock();
            return this;
        }
        if (StringCursor.isWordBreakChar(c)) {
            if (this.word) this.asyncStashInput();
        } else {
            this.word = true;
        }
        int pos = this.cursor++;
        this.bakedResult = this.bakedResult.substring(0, pos) + c + this.bakedResult.substring(pos);
        this.size++;
        this.currentEdit.append(c);
        this.lock.unlock();
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
