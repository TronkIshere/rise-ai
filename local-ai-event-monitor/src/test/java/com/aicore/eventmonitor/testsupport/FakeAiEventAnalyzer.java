package com.aicore.eventmonitor.testsupport;

import com.aicore.eventmonitor.core.analysis.AiAnalysis;
import com.aicore.eventmonitor.core.analysis.AiEventAnalyzer;
import com.aicore.eventmonitor.core.analysis.AiRuntimeState;
import com.aicore.eventmonitor.core.analysis.AiRuntimeStatus;
import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.rule.RuleFinding;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FakeAiEventAnalyzer implements AiEventAnalyzer {

    private final List<List<RuleFinding>> receivedFindings = new CopyOnWriteArrayList<>();
    private volatile Supplier<AiAnalysis> behavior = () -> AiAnalysis.skipped("not configured");

    public void returns(AiAnalysis analysis) {
        behavior = () -> analysis;
    }

    public void blocksUntilReleased(CountDownLatch entered, CountDownLatch release, AiAnalysis analysis) {
        behavior = () -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("analysis was never released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return analysis;
        };
    }

    public void fails(RuntimeException exception) {
        behavior = () -> {
            throw exception;
        };
    }

    public int getInvocationCount() {
        return receivedFindings.size();
    }

    public List<RuleFinding> getLastReceivedFindings() {
        return receivedFindings.get(receivedFindings.size() - 1);
    }

    @Override
    public String getRuntimeName() {
        return "fake";
    }

    @Override
    public AiAnalysis analyze(MonitoringEvent event, List<RuleFinding> ruleFindings) {
        receivedFindings.add(List.copyOf(ruleFindings));
        return behavior.get();
    }

    @Override
    public AiRuntimeStatus checkRuntimeStatus() {
        return AiRuntimeStatus.builder().runtime("fake").state(AiRuntimeState.AVAILABLE).build();
    }
}
