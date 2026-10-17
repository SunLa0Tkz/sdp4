package com.flightres.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleAppSmokeTest {
    @TempDir
    Path folder;

    private String type(String keys) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ConsoleApp(folder, new BufferedReader(new StringReader(keys)), new PrintStream(output, true)).run();
        return output.toString();
    }

    @Test
    void the_menu_runs_every_scenario_and_the_data_survives_a_restart() throws Exception {
        String firstRun = type("1\n4\n5\n6\n7\n0\n");
        assertTrue(firstRun.contains("SUCCESS: BK001"), firstRun);
        assertTrue(firstRun.contains("Undone steps, in this order: [A]"), firstRun);
        assertTrue(firstRun.contains("Undone steps, in this order: [B, A]"), firstRun);
        assertTrue(firstRun.contains("Undone steps, in this order: [C, B, A]"), firstRun);

        String secondRun = type("9\n10\nBK001\n12\nBK001\n\n14\n0\n");
        assertTrue(secondRun.contains("BK004 COMPENSATED"), secondRun);
        assertTrue(secondRun.contains("Cancelled. Undone steps, in this order: [C, B, A]"), secondRun);
    }
}
