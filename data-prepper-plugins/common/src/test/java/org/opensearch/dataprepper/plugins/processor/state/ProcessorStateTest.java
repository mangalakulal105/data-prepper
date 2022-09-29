/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.state;

import org.opensearch.dataprepper.processor.state.ProcessorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class ProcessorStateTest {

    protected static final Random random = new Random();

    protected ProcessorState<byte[], DataClass> processorState;

    @Before
    public abstract void setProcessorState() throws Exception;

    @Test
    public void testSize() {
        //Put two random Pojos in the processor state
        final DataClass data1 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key1 = UUID.randomUUID().toString().getBytes();

        final DataClass data2 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key2 = UUID.randomUUID().toString().getBytes();

        processorState.put(key1, data1);
        processorState.put(key2, data2);

        Assert.assertEquals(2, processorState.size());
    }

    @Test
    public void testPutAndGet() {
        //Put two random Pojos in the processor state
        final DataClass data1 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key1 = UUID.randomUUID().toString().getBytes();

        final DataClass data2 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key2 = UUID.randomUUID().toString().getBytes();

        processorState.put(key1, data1);
        processorState.put(key2, data2);

        //Read them and assert that they are correctly read, and assert incorrect key gives back null value
        Assert.assertEquals(data1, processorState.get(key1));
        Assert.assertEquals(data2, processorState.get(key2));
        Assert.assertNull(processorState.get(UUID.randomUUID().toString().getBytes()));
    }

    @Test
    public void testPutAndGetAll() {
        //Put two random Pojos in the processor state
        final DataClass data1 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key1 = UUID.randomUUID().toString().getBytes();

        final DataClass data2 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key2 = UUID.randomUUID().toString().getBytes();

        processorState.put(key1, data1);
        processorState.put(key2, data2);

        //Using byte array as key, need to translate to String key for value comparision (instead of reference)
        final Map<String, DataClass> stateMap = processorState.getAll()
                .entrySet()
                .stream()
                .collect(Collectors.toMap( dataClassEntry -> new String(dataClassEntry.getKey()), dataClassEntry -> dataClassEntry.getValue()));
        Assert.assertEquals(2, stateMap.size());
        Assert.assertEquals(data1, stateMap.get(new String(key1)));
        Assert.assertEquals(data2, stateMap.get(new String(key2)));
    }

    @After
    public void teardown() {
        processorState.delete();
    }

    @Test
    public void testIterate() {
        final DataClass data1 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key1 = UUID.randomUUID().toString().getBytes();

        final DataClass data2 = new DataClass(UUID.randomUUID().toString(), random.nextInt());
        final byte[] key2 = UUID.randomUUID().toString().getBytes();

        processorState.put(key1, data1);
        processorState.put(key2, data2);

        final Collection<String> iterateResult = processorState.iterate(new BiFunction<byte[], DataClass, String>() {
            @Override
            public String apply(byte[] s, DataClass dataClass) {
                processorState.get(s);
                return dataClass.stringVal;
            }
        });

        Assert.assertEquals(2, iterateResult.size());
        Assert.assertTrue(iterateResult.contains(data1.stringVal));
        Assert.assertTrue(iterateResult.contains(data2.stringVal));
    }

    public static class DataClass implements Serializable {
        public String stringVal;
        public int intVal;

        public DataClass(){}

        public DataClass(final String stringVal, final int intVal) {
            this.stringVal = stringVal;
            this.intVal = intVal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DataClass dataClass = (DataClass) o;
            return intVal == dataClass.intVal &&
                    Objects.equals(stringVal, dataClass.stringVal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stringVal, intVal);
        }

    }
}
