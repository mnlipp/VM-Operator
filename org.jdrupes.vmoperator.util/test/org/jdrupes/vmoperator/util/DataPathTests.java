package org.jdrupes.vmoperator.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DataPathTests {

    @Test
    void testArray() {
        int[] orig
            = { Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3) };
        var copy = DataPath.deepCopy(orig);
        for (int i = 0; i < orig.length; i++) {
            assertEquals(orig[i], copy[i]);
        }
    }
}
