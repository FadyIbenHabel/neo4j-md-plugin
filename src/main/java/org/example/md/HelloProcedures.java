package org.example.md;

import org.neo4j.procedure.*;

import java.util.stream.Stream;

public class HelloProcedures {

    public static class HelloResult {
        public String message;
        public HelloResult(String message) { this.message = message; }
    }

    @Procedure(name = "md.hello", mode = Mode.READ)
    @Description("CALL md.hello() - test procedure for modular decomposition plugin")
    public Stream<HelloResult> hello() {
        return Stream.of(new HelloResult("Modular decomposition plugin is loaded"));
    }
}
