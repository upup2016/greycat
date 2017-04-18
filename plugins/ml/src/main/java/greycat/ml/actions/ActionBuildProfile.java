/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.ml.actions;

import greycat.*;
import greycat.internal.task.TaskHelper;
import greycat.ml.MLPlugin;
import greycat.ml.profiling.Gaussian;
import greycat.struct.Buffer;
import greycat.struct.DoubleArray;

import static greycat.Tasks.newTask;

/**
 * Takes a list of feature nodes, and update the profile
 */
public class ActionBuildProfile implements Action {
    int _histogramBins;

    public ActionBuildProfile(int histogramBins) {
        this._histogramBins = histogramBins;
    }

    private static Task updateProfile =
            newTask()
                    .forEach(newTask()
                            .setAsVar("feature")
                            .thenDo(new ActionFunction() {
                                @Override
                                public void eval(TaskContext ctx) {
                                    TaskResult res = ctx.resultAsNodes();
                                    Node node = (Node) res.get(0);
                                    Gaussian.clearProfile(node);
                                    ctx.continueTask();
                                }
                            })
                            .readVar("feature")
                            .travelInTime(Constants.END_OF_TIME + "")
                            .traverse("value")
                            .setAsVar("value")
                            .timepoints(Constants.BEGINNING_OF_TIME + "", Constants.END_OF_TIME + "")
                            .setAsVar("timepoints")
                            .forEach(
                                    newTask()
                                            .setAsVar("localtime")
                                            .readVar("value")
                                            .travelInTime("{{localtime}}")
                                            .thenDo(new ActionFunction() {
                                                @Override
                                                public void eval(TaskContext ctx) {
                                                    TaskResult res = ctx.resultAsNodes();
                                                    Node node = (Node) res.get(0);

                                                    Node feature = (Node) ctx.variable("feature").get(0);
                                                    final double min = feature.getWithDefault("value_min", Double.NEGATIVE_INFINITY);
                                                    final double max = feature.getWithDefault("value_max", Double.POSITIVE_INFINITY);
                                                    Double value = (Double) node.get("value");
                                                    Gaussian.profile(feature, value, min, max);
                                                    ctx.continueWith(ctx.result());
                                                }
                                            })
                            )
                            .ifThen(new ConditionalFunction() {
                                        @Override
                                        public boolean eval(TaskContext ctx) {
                                            Node feature = (Node) ctx.variable("feature").get(0);
                                            final double pmin = feature.getWithDefault(Gaussian.MIN, 0.0);
                                            final double pmax = feature.getWithDefault(Gaussian.MAX, 0.0);
                                            return pmin != pmax;
                                        }
                                    }, newTask()
                                            .readVar("timepoints")
                                            .forEach(
                                                    newTask()
                                                            .setAsVar("localtime")
                                                            .readVar("value")
                                                            .travelInTime("{{localtime}}")
                                                            .thenDo(new ActionFunction() {
                                                                @Override
                                                                public void eval(TaskContext ctx) {
                                                                    TaskResult res = ctx.resultAsNodes();
                                                                    Node node = (Node) res.get(0);
                                                                    Node feature = (Node) ctx.variable("feature").get(0);
                                                                    final double pmin = (double) feature.get(Gaussian.MIN);
                                                                    final double pmax = (double) feature.get(Gaussian.MAX);
                                                                    final int hist = (int) ctx.variable("histogram").get(0);
                                                                    Double value = (Double) node.get("value");
                                                                    Gaussian.histogram(feature, pmin, pmax, value, hist);
                                                                    ctx.continueWith(ctx.result());
                                                                }
                                                            })


                                            )
                            )
                    ).log("profiling done")
            ;

    @Override
    public void eval(TaskContext ctx) {

        TaskContext newctx = updateProfile.prepare(ctx.graph(), ctx.result(), new Callback<TaskResult>() {
            @Override
            public void on(TaskResult result) {
                result.free();
                if (result.exception() != null) {
                    ctx.endTask(null, result.exception());
                } else {
                    ctx.continueTask();
                }
            }
        });
        newctx.setTime(ctx.time());
        newctx.setWorld(ctx.world());
        newctx.setVariable("histogram", _histogramBins);

        //ctx.setVariable("histogram", _histogramBins);
        updateProfile.executeUsing(newctx);
    }

    @Override
    public void serialize(Buffer builder) {
        builder.writeString(MLPlugin.BUILD_PROFILE);
        builder.writeChar(Constants.TASK_PARAM_OPEN);
        TaskHelper.serializeString(_histogramBins + "", builder, true);
        builder.writeChar(Constants.TASK_PARAM_CLOSE);
    }

    @Override
    public final String name() {
        return MLPlugin.BUILD_PROFILE;
    }
}