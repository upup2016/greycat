package org.mwg.internal;

import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.GraphBuilder;
import org.mwg.Type;
import org.mwg.internal.scheduler.HybridScheduler;
import org.mwg.task.TaskResult;

import static org.mwg.internal.task.CoreActions.*;
import static org.mwg.internal.task.CoreActions.newTask;

/**
 * @ignore ts
 */
@SuppressWarnings("Duplicates")
public class BenchmarkParTest {

    public static void main(String[] args) {
        Graph g = new GraphBuilder()
                .withMemorySize(1000000)
                .withScheduler(new HybridScheduler())
                //.withScheduler(new TrampolineScheduler())
                //.withScheduler(new ExecutorScheduler())
                .build();
        g.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                final long previous = System.currentTimeMillis();
                final long previousCache = g.space().available();
                newTask().loopPar("0", "9999",
                        newTask()
                                .then(createNode())
                                .then(setAttribute("name", Type.STRING, "node_{{i}}"))
                                .then(print("{{result}}"))
                                .then(addToGlobalIndex("nodes", "name"))
                                .loop("0", "999",
                                        newTask().then(travelInTime("{{i}}")).then(setAttribute("val", Type.INT, "{{i}}")).then(clearResult()))
                                .ifThen(cond("i % 100 == 0"), newTask().then(save()))
                                .then(clearResult())
                ).then(save()).then(readGlobalIndex("nodes")).execute(g, new Callback<TaskResult>() {
                    @Override
                    public void on(TaskResult result) {
                        System.out.println("indexSize=" + result.size());
                        result.free();
                        long after = System.currentTimeMillis();
                        long afterCache = g.space().available();
                        System.out.println(after - previous + "ms");
                        System.out.println(previousCache + "-" + afterCache);
                        g.disconnect(new Callback<Boolean>() {
                            @Override
                            public void on(Boolean result) {
                            }
                        });
                    }
                });
            }
        });
    }


}