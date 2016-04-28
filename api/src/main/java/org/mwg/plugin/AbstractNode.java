package org.mwg.plugin;

import org.mwg.*;
import org.mwg.struct.LongLongArrayMap;
import org.mwg.struct.LongLongArrayMapCallBack;
import org.mwg.struct.Map;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base implementation to develop NodeFactory plugins without overriding every methods
 */
public abstract class AbstractNode implements Node {

    private final long _world;

    private final long _time;

    private final long _id;

    private final Graph _graph;

    protected final Resolver _resolver;

    public final AtomicReference<long[]> _previousResolveds;

    public AbstractNode(long p_world, long p_time, long p_id, Graph p_graph, long[] currentResolution) {
        this._world = p_world;
        this._time = p_time;
        this._id = p_id;
        this._graph = p_graph;
        this._resolver = p_graph.resolver();
        this._previousResolveds = new AtomicReference<long[]>();
        this._previousResolveds.set(currentResolution);
    }

    protected NodeState unphasedState() {
        return this._resolver.resolveState(this, true);
    }

    protected NodeState phasedState() {
        return this._resolver.resolveState(this, false);
    }

    protected NodeState newState(long time) {
        return this._resolver.newState(this, _world, time);
    }

    @Override
    public Graph graph() {
        return _graph;
    }

    @Override
    public long world() {
        return this._world;
    }

    @Override
    public long time() {
        return this._time;
    }

    @Override
    public long id() {
        return this._id;
    }

    @Override
    public Object get(String propertyName) {
        NodeState resolved = this._resolver.resolveState(this, true);
        if (resolved != null) {
            return resolved.get(this._resolver.stringToLongKey(propertyName));
        }
        return null;
    }

    /**
     * @native ts
     */
    @Override
    public void set(String propertyName, Object propertyValue) {
        if (propertyValue instanceof String) {
            setProperty(propertyName, Type.STRING, propertyValue);
        } else if (propertyValue instanceof Double) {
            setProperty(propertyName, Type.DOUBLE, propertyValue);
        } else if (propertyValue instanceof Long) {
            setProperty(propertyName, Type.LONG, propertyValue);
        } else if (propertyValue instanceof Float) {
            setProperty(propertyName, Type.DOUBLE, (double) ((float) propertyValue));
        } else if (propertyValue instanceof Integer) {
            setProperty(propertyName, Type.INT, propertyValue);
        } else if (propertyValue instanceof Boolean) {
            setProperty(propertyName, Type.BOOL, propertyValue);
        } else if (propertyValue instanceof int[]) {
            setProperty(propertyName, Type.INT_ARRAY, propertyValue);
        } else if (propertyValue instanceof double[]) {
            setProperty(propertyName, Type.DOUBLE_ARRAY, propertyValue);
        } else if (propertyValue instanceof long[]) {
            setProperty(propertyName, Type.LONG_ARRAY, propertyValue);
        } else {
            throw new RuntimeException("Invalid property type: " + propertyValue + ", please use a Type listed in org.mwg.Type");
        }
    }

    @Override
    public void setProperty(String propertyName, byte propertyType, Object propertyValue) {
        NodeState preciseState = this._resolver.resolveState(this, false);
        if (preciseState != null) {
            preciseState.set(this._resolver.stringToLongKey(propertyName), propertyType, propertyValue);
        } else {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
    }

    @Override
    public Map map(String propertyName, byte propertyType) {
        NodeState preciseState = this._resolver.resolveState(this, false);
        if (preciseState != null) {
            return (Map) preciseState.getOrCreate(this._resolver.stringToLongKey(propertyName), propertyType);
        } else {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
    }

    @Override
    public byte type(String propertyName) {
        NodeState resolved = this._resolver.resolveState(this, true);
        if (resolved != null) {
            return resolved.getType(this._resolver.stringToLongKey(propertyName));
        }
        return -1;
    }

    @Override
    public void removeProperty(String attributeName) {
        setProperty(attributeName, Type.INT, null);
    }

    @Override
    public <A extends Node> void rel(String relationName, Callback<A[]> callback) {
        if (callback == null) {
            return;
        }
        final NodeState resolved = this._resolver.resolveState(this, true);
        if (resolved != null) {
            final long[] flatRefs = (long[]) resolved.get(this._resolver.stringToLongKey(relationName));
            if (flatRefs == null || flatRefs.length == 0) {
                callback.on((A[]) new Node[0]);
            } else {
                final A[] result = (A[]) new Node[flatRefs.length];
                final DeferCounter counter = _graph.counter(flatRefs.length);
                final int[] resultIndex = new int[1];
                for (int i = 0; i < flatRefs.length; i++) {
                    this._resolver.lookup(_world, _time, flatRefs[i], new Callback<Node>() {
                        @Override
                        public void on(Node kNode) {
                            if (kNode != null) {
                                result[resultIndex[0]] = (A) kNode;
                                resultIndex[0]++;
                            }
                            counter.count();
                        }
                    });
                }
                counter.then(new Callback() {
                    @Override
                    public void on(Object o) {
                        if (resultIndex[0] == result.length) {
                            callback.on(result);
                        } else {
                            A[] toSend = (A[]) new Node[resultIndex[0]];
                            System.arraycopy(result, 0, toSend, 0, toSend.length);
                            callback.on(toSend);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void add(String relationName, Node relatedNode) {
        NodeState preciseState = this._resolver.resolveState(this, false);
        long relationKey = this._resolver.stringToLongKey(relationName);
        if (preciseState != null) {
            long[] previous = (long[]) preciseState.get(relationKey);
            if (previous == null) {
                previous = new long[1];
                previous[0] = relatedNode.id();
            } else {
                long[] incArray = new long[previous.length + 1];
                System.arraycopy(previous, 0, incArray, 0, previous.length);
                incArray[previous.length] = relatedNode.id();
                previous = incArray;
            }
            preciseState.set(relationKey, Type.LONG_ARRAY, previous);
        } else {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
    }

    @Override
    public void remove(String relationName, Node relatedNode) {
        NodeState preciseState = this._resolver.resolveState(this, false);
        long relationKey = this._resolver.stringToLongKey(relationName);
        if (preciseState != null) {
            long[] previous = (long[]) preciseState.get(relationKey);
            if (previous != null) {
                int indexToRemove = -1;
                for (int i = 0; i < previous.length; i++) {
                    if (previous[i] == relatedNode.id()) {
                        indexToRemove = i;
                        break;
                    }
                }
                if (indexToRemove != -1) {
                    if ((previous.length - 1) == 0) {
                        preciseState.set(relationKey, Type.LONG_ARRAY, null);
                    } else {
                        long[] newArray = new long[previous.length - 1];
                        System.arraycopy(previous, 0, newArray, 0, indexToRemove);
                        System.arraycopy(previous, indexToRemove + 1, newArray, indexToRemove, previous.length - indexToRemove - 1);
                        preciseState.set(relationKey, Type.LONG_ARRAY, newArray);
                    }
                }
            }
        } else {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
    }

    @Override
    public void free() {
        this._resolver.freeNode(this);
    }

    @Override
    public long timeDephasing() {
        NodeState state = this._resolver.resolveState(this, true);
        if (state != null) {
            return (this._time - state.time());
        } else {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
    }

    @Override
    public void forcePhase() {
        this._resolver.resolveState(this, false);
    }

    @Override
    public void timepoints(long beginningOfSearch, long endOfSearch, Callback<long[]> callback) {
        this._resolver.resolveTimepoints(this, beginningOfSearch, endOfSearch, callback);
    }

    @Override
    public <A extends Node> void jump(long targetTime, Callback<A> callback) {
        _resolver.lookup(_world, targetTime, _id, callback);
    }

    @Override
    public <A extends org.mwg.Node> void findAt(String indexName, long world, long time, String query, Callback<A[]> callback) {
        NodeState currentNodeState = this._resolver.resolveState(this, false);
        if (currentNodeState == null) {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
        LongLongArrayMap indexMap = (LongLongArrayMap) currentNodeState.get(this._resolver.stringToLongKey(indexName));
        if (indexMap != null) {
            final AbstractNode selfPointer = this;
            final Query flatQuery = Query.parseQuery(query, selfPointer._resolver);
            final long[] foundId = indexMap.get(flatQuery.hash());
            if (foundId == null) {
                callback.on((A[]) new org.mwg.Node[0]);
                return;
            }
            final org.mwg.Node[] resolved = new org.mwg.Node[foundId.length];
            final DeferCounter waiter = _graph.counter(foundId.length);
            //TODO replace by a par lookup
            final AtomicInteger nextResolvedTabIndex = new AtomicInteger(0);

            for (int i = 0; i < foundId.length; i++) {
                selfPointer._resolver.lookup(world, time, foundId[i], new Callback<org.mwg.Node>() {
                    @Override
                    public void on(org.mwg.Node resolvedNode) {
                        if (resolvedNode != null) {
                            resolved[nextResolvedTabIndex.getAndIncrement()] = resolvedNode;
                        }
                        waiter.count();
                    }
                });
            }
            waiter.then(new Callback() {
                @Override
                public void on(Object o) {
                    //select
                    A[] resultSet = (A[]) new org.mwg.Node[nextResolvedTabIndex.get()];
                    int resultSetIndex = 0;

                    for (int i = 0; i < resultSet.length; i++) {
                        org.mwg.Node resolvedNode = resolved[i];
                        NodeState resolvedState = selfPointer._resolver.resolveState(resolvedNode, true);
                        boolean exact = true;
                        for (int j = 0; j < flatQuery.size; j++) {
                            Object obj = resolvedState.get(flatQuery.attributes[j]);
                            if (flatQuery.values[j] == null) {
                                if (obj != null) {
                                    exact = false;
                                    break;
                                }
                            } else {
                                if (obj == null) {
                                    exact = false;
                                    break;
                                } else {
                                    if (!Constants.equals(flatQuery.values[j], obj.toString())) {
                                        exact = false;
                                        break;
                                    }
                                }
                            }
                        }
                        if (exact) {
                            resultSet[resultSetIndex] = (A) resolvedNode;
                            resultSetIndex++;
                        }
                    }
                    if (resultSet.length == resultSetIndex) {
                        callback.on(resultSet);
                    } else {
                        A[] trimmedResultSet = (A[]) new org.mwg.Node[resultSetIndex];
                        System.arraycopy(resultSet, 0, trimmedResultSet, 0, resultSetIndex);
                        callback.on(trimmedResultSet);
                    }
                }
            });
        } else {
            callback.on((A[]) new org.mwg.Node[0]);
        }
    }

    @Override
    public <A extends org.mwg.Node> void find(String indexName, String query, Callback<A[]> callback) {
        findAt(indexName, time(), world(), query, callback);
    }

    @Override
    public <A extends org.mwg.Node> void allAt(String indexName, long world, long time, Callback<A[]> callback) {
        NodeState currentNodeState = this._resolver.resolveState(this, false);
        if (currentNodeState == null) {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
        LongLongArrayMap indexMap = (LongLongArrayMap) currentNodeState.get(this._resolver.stringToLongKey(indexName));
        if (indexMap != null) {
            final AbstractNode selfPointer = this;
            int mapSize = (int) indexMap.size();
            final A[] resolved = (A[]) new org.mwg.Node[mapSize];
            DeferCounter waiter = _graph.counter(mapSize);
            //TODO replace by a parralel lookup
            final AtomicInteger loopInteger = new AtomicInteger(0);
            indexMap.each(new LongLongArrayMapCallBack() {
                @Override
                public void on(final long hash, final long nodeId) {
                    selfPointer._resolver.lookup(world, time, nodeId, new Callback<org.mwg.Node>() {
                        @Override
                        public void on(org.mwg.Node resolvedNode) {
                            resolved[loopInteger.getAndIncrement()] = (A) resolvedNode;
                            waiter.count();
                        }
                    });
                }
            });
            waiter.then(new Callback() {
                @Override
                public void on(Object o) {
                    if (loopInteger.get() == resolved.length) {
                        callback.on(resolved);
                    } else {
                        A[] toSend = (A[]) new org.mwg.Node[loopInteger.get()];
                        System.arraycopy(resolved, 0, toSend, 0, toSend.length);
                        callback.on(toSend);
                    }
                }
            });
        } else {
            callback.on((A[]) new org.mwg.Node[0]);
        }
    }

    @Override
    public <A extends org.mwg.Node> void all(String indexName, Callback<A[]> callback) {
        allAt(indexName, world(), time(), callback);
    }

    @Override
    public void index(String indexName, org.mwg.Node nodeToIndex, String[] keyAttributes, Callback<Boolean> callback) {
        NodeState currentNodeState = this._resolver.resolveState(this, true);
        if (currentNodeState == null) {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
        LongLongArrayMap indexMap = (LongLongArrayMap) currentNodeState.getOrCreate(this._resolver.stringToLongKey(indexName), Type.LONG_LONG_ARRAY_MAP);
        Query flatQuery = new Query();
        NodeState toIndexNodeState = this._resolver.resolveState(nodeToIndex, true);
        for (int i = 0; i < keyAttributes.length; i++) {
            long attKey = this._resolver.stringToLongKey(keyAttributes[i]);
            Object attValue = toIndexNodeState.get(attKey);
            if (attValue != null) {
                flatQuery.add(attKey, attValue.toString());
            } else {
                flatQuery.add(attKey, null);
            }
        }
        flatQuery.compute();
        //TODO AUTOMATIC UPDATE
        indexMap.put(flatQuery.hash(), nodeToIndex.id());

        if (Constants.isDefined(callback)) {
            callback.on(true);
        }
    }

    @Override
    public void unindex(String indexName, org.mwg.Node nodeToIndex, String[] keyAttributes, Callback<Boolean> callback) {
        NodeState currentNodeState = this._resolver.resolveState(this, true);
        if (currentNodeState == null) {
            throw new RuntimeException(Constants.CACHE_MISS_ERROR);
        }
        LongLongArrayMap indexMap = (LongLongArrayMap) currentNodeState.get(this._resolver.stringToLongKey(indexName));
        if (indexMap != null) {
            Query flatQuery = new Query();
            NodeState toIndexNodeState = this._resolver.resolveState(nodeToIndex, true);
            for (int i = 0; i < keyAttributes.length; i++) {
                long attKey = this._resolver.stringToLongKey(keyAttributes[i]);
                Object attValue = toIndexNodeState.get(attKey);
                if (attValue != null) {
                    flatQuery.add(attKey, attValue.toString());
                } else {
                    flatQuery.add(attKey, null);
                }
            }
            flatQuery.compute();
            //TODO AUTOMATIC UPDATE
            indexMap.remove(flatQuery.hash(), nodeToIndex.id());
        }
        if (Constants.isDefined(callback)) {
            callback.on(true);
        }
    }


}