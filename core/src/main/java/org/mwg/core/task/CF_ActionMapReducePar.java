package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.Constants;
import org.mwg.DeferCounter;
import org.mwg.plugin.Job;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Action;
import org.mwg.task.Task;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

import java.util.Map;

class CF_ActionMapReducePar extends CF_Action {

    private final Task[] _subTasks;

    CF_ActionMapReducePar(final Task... p_subTasks) {
        super();
        _subTasks = p_subTasks;
    }

    @Override
    public void eval(final TaskContext ctx) {
        final TaskResult previous = ctx.result();
        final TaskResult next = ctx.newResult();
        final int subTasksSize = _subTasks.length;
        next.allocate(subTasksSize);
        final DeferCounter waiter = ctx.graph().newCounter(subTasksSize);
        for (int i = 0; i < subTasksSize; i++) {
            int finalI = i;
            _subTasks[i].executeFrom(ctx, previous, SchedulerAffinity.ANY_LOCAL_THREAD, new Callback<TaskResult>() {
                @Override
                public void on(TaskResult subTaskResult) {
                    next.set(finalI, subTaskResult);
                    waiter.count();
                }
            });
        }
        waiter.then(new Job() {
            @Override
            public void run() {
                ctx.continueWith(next);
            }
        });
    }

    @Override
    public Task[] children() {
        return _subTasks;
    }

    @Override
    public void cf_serialize(StringBuilder builder, Map<Integer, Integer> dagIDS) {
        builder.append(ActionNames.LOOP);
        builder.append(Constants.TASK_PARAM_OPEN);
        for (int i = 0; i < _subTasks.length; i++) {
            if (i != 0) {
                builder.append(Constants.TASK_PARAM_SEP);
            }
            final CoreTask castedAction = (CoreTask) _subTasks[i];
            final int castedActionHash = castedAction.hashCode();
            if (dagIDS == null || !dagIDS.containsKey(castedActionHash)) {
                castedAction.serialize(builder, dagIDS);
            } else {
                builder.append("" + dagIDS.get(castedActionHash));
            }
        }
        builder.append(Constants.TASK_PARAM_CLOSE);
    }

}