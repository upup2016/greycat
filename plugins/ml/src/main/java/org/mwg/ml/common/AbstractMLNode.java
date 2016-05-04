package org.mwg.ml.common;

import org.mwg.Graph;
import org.mwg.ml.common.mathexp.KMathExpressionEngine;
import org.mwg.ml.common.mathexp.impl.MathExpressionEngine;
import org.mwg.plugin.AbstractNode;

/**
 * Created by assaad on 04/05/16.
 */
public abstract class AbstractMLNode extends AbstractNode {
    private static String _SEPARATOR="#";
    public static String FEATURES_QUERY_KEY="_FEATURES_QUERY";


    public AbstractMLNode(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
        super(p_world, p_time, p_id, p_graph, currentResolution);
    }

    @Override
    public Object get(String propertyName) {
        if (propertyName != null && propertyName.startsWith("$")) {
            Object expressionObj = super.get(propertyName.substring(1));
            //ToDo this is dangerous for infinite loops or circular dependency, to fix
            KMathExpressionEngine localEngine = MathExpressionEngine.parse(expressionObj.toString());
            return localEngine.eval(this);
        } else {
            return super.get(propertyName);
        }
    }

    public double[] extractFeatures(){
        String query= (String)super.get(FEATURES_QUERY_KEY);
        if(query!=null) {
            String[] split = query.split(_SEPARATOR);
            double[] result = new double[split.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = (double) get(split[i]);
            }
            return result;
        }
        else{
            return null;
        }
    }


}
