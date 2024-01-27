package io.github.nerjalnosk.stringcursor;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

public class StringCursorMethodOrderer implements MethodOrderer {
    @Override
    public void orderMethods(MethodOrdererContext methodOrdererContext) {
        methodOrdererContext.getMethodDescriptors().sort((o1, o2) -> {
            int i1 = Integer.parseInt(o1.getDisplayName().split("_")[0].substring(4));
            int i2 = Integer.parseInt(o2.getDisplayName().split("_")[0].substring(4));
            return Integer.compare(i1, i2);
        });
    }
}
