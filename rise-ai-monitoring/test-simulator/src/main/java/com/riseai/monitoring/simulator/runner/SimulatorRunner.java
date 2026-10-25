package com.riseai.monitoring.simulator.runner;

import com.riseai.monitoring.simulator.configuration.SimulatorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimulatorRunner implements ApplicationRunner, ExitCodeGenerator {

    private final SimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private int exitCode;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        exitCode = new SimulationRun(properties, objectMapper, System.out).execute();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
