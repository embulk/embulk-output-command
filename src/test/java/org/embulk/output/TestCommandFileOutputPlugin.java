package org.embulk.output;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.embulk.EmbulkTestRuntime;
import org.embulk.output.CommandFileOutputPlugin.ShellFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestCommandFileOutputPlugin
{
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    private ShellFactory shellFactory;

    @Before
    public void createResources()
    {
        shellFactory = new ShellFactory().build();
    }

    @Test
    public void testShell() {
        List<String> shell = shellFactory.get();
        String osName = System.getProperty("os.name");
        List<String> actualShellCmd;
        if (osName.indexOf("Windows") >= 0) {
            actualShellCmd = ImmutableList.of("PowerShell.exe", "-Command");
        } else {
            actualShellCmd = ImmutableList.of("sh", "-c");
        }
        assertEquals(actualShellCmd, shell);
    }
}
