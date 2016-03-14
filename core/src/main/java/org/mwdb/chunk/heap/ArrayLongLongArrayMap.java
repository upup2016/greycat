
package org.mwdb.chunk.heap;

import org.mwdb.Constants;
import org.mwdb.chunk.KChunkListener;
import org.mwdb.chunk.KLongLongArrayMap;
import org.mwdb.chunk.KLongLongArrayMapCallBack;
import org.mwdb.utility.PrimitiveHelper;

import java.util.concurrent.atomic.AtomicReference;

public class ArrayLongLongArrayMap implements KLongLongArrayMap {

    private final AtomicReference<InternalState> state;

    private final KChunkListener _listener;

    private volatile boolean aligned;

    public ArrayLongLongArrayMap(KChunkListener p_listener, int initialCapacity, ArrayLongLongArrayMap p_origin) {
        this._listener = p_listener;
        this.state = new AtomicReference<InternalState>();
        if (p_origin == null) {
            InternalState newstate = new InternalState(initialCapacity, new long[initialCapacity], new long[initialCapacity], new int[initialCapacity], new int[initialCapacity], new int[initialCapacity], 0);
            for (int i = 0; i < initialCapacity; i++) {
                newstate._elementNext[i] = -1;
                newstate._elementHash[i] = -1;
                newstate._columnSize[i] = 0;
            }
            this.state.set(newstate);
            aligned = true;
        } else {
            this.state.set(p_origin.state.get());
            aligned = false;
        }
    }

    /**
     * Internal Map state, to be replace in a compare and swap manner
     */
    final class InternalState {

        public final int _stateSize;

        public final long[] _elementK;

        public final long[] _elementV;

        public final int[] _elementNext;

        public final int[] _elementHash;

        public final int[] _columnSize;

        public final int _threshold;

        protected volatile int _elementCount;

        public InternalState(int p_stateSize, long[] p_elementK, long[] p_elementV, int[] p_elementNext, int[] p_elementHash, int[] p_columnSize, int p_elementCount) {
            this._stateSize = p_stateSize;
            this._elementK = p_elementK;
            this._elementV = p_elementV;
            this._elementNext = p_elementNext;
            this._elementHash = p_elementHash;
            this._elementCount = p_elementCount;
            this._columnSize = p_columnSize;
            this._threshold = (int) (p_stateSize * Constants.MAP_LOAD_FACTOR);
        }

        public InternalState clone() {
            long[] cloned_elementK = new long[_stateSize];
            System.arraycopy(_elementK, 0, cloned_elementK, 0, _stateSize);
            long[] cloned_elementV = new long[_stateSize];
            System.arraycopy(_elementV, 0, cloned_elementV, 0, _stateSize);
            int[] cloned_elementNext = new int[_stateSize];
            System.arraycopy(_elementNext, 0, cloned_elementNext, 0, _stateSize);
            int[] cloned_elementHash = new int[_stateSize];
            System.arraycopy(_elementHash, 0, cloned_elementHash, 0, _stateSize);
            int[] cloned_columnSize = new int[_stateSize];
            System.arraycopy(_columnSize, 0, cloned_columnSize, 0, _stateSize);
            return new InternalState(_stateSize, cloned_elementK, cloned_elementV, cloned_elementNext, cloned_elementHash, cloned_columnSize, _elementCount);
        }

    }

    @Override
    public final long[] get(long key) {
        InternalState internalState = state.get();
        if (internalState._stateSize == 0) {
            return new long[0];
        }
        int hashIndex = (int) PrimitiveHelper.longHash(key, internalState._stateSize);
        int hashColumnSize = internalState._columnSize[hashIndex];
        long[] result = new long[hashColumnSize];
        int columnIndex = 0;
        int m = internalState._elementHash[hashIndex];
        while (m >= 0) {
            if (key == internalState._elementK[m]) {
                result[columnIndex] = internalState._elementV[m];
                columnIndex++;
            }
            m = internalState._elementNext[m];
        }
        if (hashColumnSize == columnIndex) {
            return result;
        } else {
            //shrink result
            long[] shrinkedResult = new long[columnIndex];
            System.arraycopy(result, 0, shrinkedResult, 0, columnIndex);
            return shrinkedResult;
        }
    }

    @Override
    public final void each(KLongLongArrayMapCallBack callback) {
        InternalState internalState = state.get();
        for (int i = 0; i < internalState._elementCount; i++) {
            if (internalState._elementNext[i] != -1) { //there is a real value
                callback.on(internalState._elementK[i], internalState._elementV[i]);
            }
        }
    }

    @Override
    public long size() {
        return state.get()._elementCount;
    }

    @Override
    public final synchronized void put(long key, long value) {

        if (!aligned) {
            //clone the state
            state.set(state.get().clone());
            aligned = true;
        }

        //first test if reHash is necessary
        InternalState internalState = state.get();
        if ((internalState._elementCount + 1) > internalState._threshold) {
            int newCapacity = internalState._stateSize << 1;
            long[] newElementK = new long[newCapacity];
            long[] newElementV = new long[newCapacity];
            System.arraycopy(internalState._elementK, 0, newElementK, 0, internalState._stateSize);
            System.arraycopy(internalState._elementV, 0, newElementV, 0, internalState._stateSize);
            int[] newElementNext = new int[newCapacity];
            int[] newElementHash = new int[newCapacity];
            int[] newHashSize = new int[newCapacity];
            for (int i = 0; i < newCapacity; i++) {
                newElementNext[i] = -1;
                newElementHash[i] = -1;
                newHashSize[i] = 0;
            }
            //rehashEveryThing
            for (int i = 0; i < internalState._elementCount; i++) {
                if (internalState._elementNext[i] != -1) { //there is a real value
                    int newHashIndex = (int) PrimitiveHelper.longHash(internalState._elementK[i], newCapacity);
                    int currentHashedIndex = newElementHash[newHashIndex];
                    if (currentHashedIndex != -1) {
                        newElementNext[i] = currentHashedIndex;
                    } else {
                        newElementNext[i] = -2;
                    }
                    newHashSize[newHashIndex] = newHashSize[newHashIndex] + 1;
                    newElementHash[newHashIndex] = i;
                }
            }
            internalState = new InternalState(newCapacity, newElementK, newElementV, newElementNext, newElementHash, newHashSize, internalState._elementCount);
            state.set(internalState);
        }
        int hashIndex = (int) PrimitiveHelper.longHash(key, internalState._stateSize);
        int m = internalState._elementHash[hashIndex];
        while (m >= 0) {
            if (key == internalState._elementK[m] && value == internalState._elementV[m]) {
                return;
            }
            m = internalState._elementNext[m];
        }
        int newIndex = internalState._elementCount;
        internalState._elementK[newIndex] = key;
        internalState._elementV[newIndex] = value;
        int currentHashedElemIndex = internalState._elementHash[hashIndex];
        if (currentHashedElemIndex != -1) {
            internalState._elementNext[newIndex] = currentHashedElemIndex;
        } else {
            internalState._elementNext[newIndex] = -2;
        }
        internalState._columnSize[hashIndex] = internalState._columnSize[hashIndex] + 1;
        internalState._elementHash[hashIndex] = newIndex;
        internalState._elementCount = internalState._elementCount + 1;
        _listener.declareDirty(null);
    }

    @Override
    public void remove(long key, long value) {
        throw new RuntimeException("Not implemented yet!!!");
    }

}



