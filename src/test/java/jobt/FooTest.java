package jobt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import jobt.plugin.TaskStatus;

public class FooTest {

    @Test
    public void test() {
        assertEquals("OK", TaskStatus.OK.name());
    }

}