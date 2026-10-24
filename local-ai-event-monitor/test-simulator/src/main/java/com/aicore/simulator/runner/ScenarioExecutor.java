package com.aicore.simulator.runner;

import com.aicore.simulator.client.CoreClient;
import com.aicore.simulator.client.CoreResponse;
import com.aicore.simulator.evaluation.ScenarioEvaluator;
import com.aicore.simulator.evaluation.ScenarioResult;
import com.aicore.simulator.scenario.Scenario;
import com.aicore.simulator.scenario.ScenarioGenerator;
import com.aicore.simulator.scenario.ScenarioStep;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class ScenarioExecutor {

    private final CoreClient client;
    private final ScenarioEvaluator evaluator;
    private final int concurrency;

    public ScenarioExecutor(CoreClient client, ScenarioEvaluator evaluator, int concurrency) {
        this.client = client;
        this.evaluator = evaluator;
        this.concurrency = concurrency;
    }

    public void run(ScenarioGenerator generator, Consumer<ScenarioResult> sink, IntConsumer progress) throws InterruptedException {
        int total = generator.getScenarioCount();
        AtomicInteger next = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        for (int worker = 0; worker < concurrency; worker++) {
            workers.execute(() -> {
                int index;
                while ((index = next.getAndIncrement()) < total) {
                    sink.accept(runScenario(generator, index));
                    progress.accept(completed.incrementAndGet());
                }
            });
        }
        workers.shutdown();
        workers.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    ScenarioResult runScenario(ScenarioGenerator generator, int index) {
        try {
            Scenario scenario = generator.generate(index);
            List<CoreResponse> responses = new ArrayList<>();
            for (ScenarioStep step : scenario.getSteps()) {
                CoreResponse response = client.postEvent(step.getBody(), step.getContentType());
                responses.add(response);
                if (!response.isReceived()) {
                    break;
                }
            }
            return evaluator.evaluate(scenario, responses);
        } catch (RuntimeException ex) {
            return ScenarioResult.simulatorError(index, generator.categoryOf(index), ex);
        }
    }
}
