package com.hazelcast.spi.impl.operationexecutor.impl;

import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.internal.properties.GroupProperty.GENERIC_OPERATION_THREAD_COUNT;
import static com.hazelcast.internal.properties.GroupProperty.PARTITION_COUNT;
import static com.hazelcast.internal.properties.GroupProperty.PARTITION_OPERATION_THREAD_COUNT;
import static com.hazelcast.internal.properties.GroupProperty.PRIORITY_GENERIC_OPERATION_THREAD_COUNT;
import static com.hazelcast.spi.Operation.GENERIC_PARTITION_ID;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class OperationExecutorImpl_BasicTest extends OperationExecutorImpl_AbstractTest {

    @Test
    public void testConstruction() {
        initExecutor();

        assertEquals(props.getInteger(PARTITION_COUNT), executor.getPartitionOperationRunners().length);
        assertEquals(executor.getGenericThreadCount(), executor.getGenericOperationRunners().length);

        assertEquals(props.getInteger(PARTITION_OPERATION_THREAD_COUNT),
                executor.getPartitionThreadCount());

        assertEquals(props.getInteger(GENERIC_OPERATION_THREAD_COUNT) + props.getInteger(PRIORITY_GENERIC_OPERATION_THREAD_COUNT),
                executor.getGenericThreadCount());
    }

    @Test
    public void test_getRunningOperationCount() {
        initExecutor();

        executor.execute(new DummyOperation(GENERIC_PARTITION_ID).durationMs(2000));
        executor.execute(new DummyOperation(GENERIC_PARTITION_ID).durationMs(2000));

        executor.execute(new DummyOperation(0).durationMs(2000));

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                int runningOperationCount = executor.getRunningOperationCount();
                System.out.println("runningOperationCount:" + runningOperationCount);
                assertEquals(3, runningOperationCount);
            }
        });
    }

    @Test
    public void test_getQueueSize() {
        config.setProperty(PRIORITY_GENERIC_OPERATION_THREAD_COUNT.getName(), "0");
        initExecutor();

        // first we need to set the threads to work so the queues are going to be left alone.
        for (int k = 0; k < executor.getGenericThreadCount(); k++) {
            executor.execute(new DummyOperation(GENERIC_PARTITION_ID).durationMs(2000));
        }
        for (int k = 0; k < executor.getPartitionThreadCount(); k++) {
            executor.execute(new DummyOperation(k).durationMs(2000));
        }

        // now we throw in some work in the queues that wont' be picked up
        int count = 0;
        for (int l = 0; l < 3; l++) {
            for (int k = 0; k < executor.getGenericThreadCount(); k++) {
                executor.execute(new DummyOperation(GENERIC_PARTITION_ID).durationMs(2000));
                count++;
            }
        }
        for (int l = 0; l < 5; l++) {
            for (int k = 0; k < executor.getPartitionThreadCount(); k++) {
                executor.execute(new DummyOperation(k).durationMs(2000));
                count++;
            }
        }

        final int expectedCount = count;
        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(expectedCount, executor.getQueueSize());
            }
        });
    }

    @Test(expected = NullPointerException.class)
    public void test_runOnAllPartitionThreads_whenTaskNull() {
        initExecutor();
        executor.executeOnPartitionThreads(null);
    }

    @Test
    public void test_runOnAllPartitionThreads() throws Exception {
        initExecutor();

        int threadCount = executor.getPartitionThreadCount();
        final CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);

        executor.executeOnPartitionThreads(new Runnable() {
            @Override
            public void run() {
                // current thread must be a PartitionOperationThread
                if (Thread.currentThread() instanceof PartitionOperationThread) {
                    try {
                        awaitBarrier(barrier);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        awaitBarrier(barrier);
    }

    @Test
    public void test_interruptAllPartitionThreads() throws Exception {
        initExecutor();

        int threadCount = executor.getPartitionThreadCount();
        final CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);

        executor.executeOnPartitionThreads(new Runnable() {
            @Override
            public void run() {
                // current thread must be a PartitionOperationThread
                if (Thread.currentThread() instanceof PartitionOperationThread) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException ignored) {
                    } finally {
                        try {
                            awaitBarrier(barrier);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });

        executor.interruptPartitionThreads();
        awaitBarrier(barrier);
    }

    @Test
    public void genericPriorityTaskIsPickedUpEvenWhenAllGenericThreadsBusy() {
        initExecutor();

        // lets keep the regular generic threads busy
        for (int k = 0; k < executor.getGenericThreadCount() * 10; k++) {
            executor.execute(new DummyOperation(GENERIC_PARTITION_ID).durationMs(20000000));
        }

        final CountDownLatch open = new CountDownLatch(1);
        // then we schedule a priority task
        executor.execute(new UrgentDummyOperation(GENERIC_PARTITION_ID) {
            public void run() {
                open.countDown();
            }
        });

        // and verify it completes.
        assertOpenEventually(open);
    }

    private static void awaitBarrier(CyclicBarrier barrier) throws Exception {
        barrier.await(ASSERT_TRUE_EVENTUALLY_TIMEOUT, TimeUnit.SECONDS);
    }
}
