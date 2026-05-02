package com.herbscript.agent.orchestrator;

public interface AgentStreamSink {

    void emit(String eventName, Object payload);
}
