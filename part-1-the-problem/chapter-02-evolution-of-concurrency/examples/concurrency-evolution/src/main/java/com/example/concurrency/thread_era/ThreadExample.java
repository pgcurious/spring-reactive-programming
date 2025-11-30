package com.example.concurrency.thread_era;

import java.util.LinkedList;
import java.util.Queue;

/**
 * THE THREAD ERA (Java 1.0 - 1.4)
 *
 * In the early days of Java, concurrency meant direct thread management.
 * This was powerful but dangerous:
 * - Easy to create resource leaks
 * - Easy to cause deadlocks
 * - Hard to coordinate between threads
 * - wait/notify semantics are subtle and error-prone
 */
public class ThreadExample {

    /**
     * Demonstrates basic thread creation and management.
     * Notice how we manually create and start threads.
     */
    public void demonstrateBasicThread() {
        System.out.println("1. Basic Thread Creation:");
        System.out.println("   Creating and starting a thread manually...\n");

        Thread thread = new Thread(() -> {
            System.out.println("    [" + Thread.currentThread().getName() +
                "] Hello from the thread era!");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("    [" + Thread.currentThread().getName() +
                "] Thread work complete.");
        }, "worker-thread-1");

        thread.start();

        try {
            thread.join(); // Wait for thread to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("    Main thread: Worker finished.\n");
    }

    /**
     * Demonstrates the classic Producer-Consumer pattern using wait/notify.
     * This was the standard way to coordinate threads before Java 5.
     *
     * Notice the complexity:
     * - Must use synchronized blocks
     * - Must call wait() in a while loop (spurious wakeups!)
     * - Easy to forget notify() and cause deadlocks
     * - Hard to reason about correctness
     */
    public void demonstrateProducerConsumer() {
        System.out.println("2. Producer-Consumer with wait/notify:");
        System.out.println("   This pattern required careful synchronization...\n");

        ProducerConsumerBuffer buffer = new ProducerConsumerBuffer(3);

        // Producer thread
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                try {
                    buffer.produce(i);
                    Thread.sleep(50); // Simulate varying production rate
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "producer");

        // Consumer thread
        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    buffer.consume();
                    Thread.sleep(100); // Simulate slower consumption
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "consumer");

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("    Producer-Consumer demo complete.\n");
    }

    /**
     * Classic bounded buffer implementation using wait/notify.
     * This is the kind of code that was common before BlockingQueue.
     */
    static class ProducerConsumerBuffer {
        private final Queue<Integer> buffer = new LinkedList<>();
        private final int capacity;

        public ProducerConsumerBuffer(int capacity) {
            this.capacity = capacity;
        }

        /**
         * Add an item to the buffer.
         * Blocks if buffer is full.
         */
        public synchronized void produce(int value) throws InterruptedException {
            // MUST use while, not if! (spurious wakeups)
            while (buffer.size() == capacity) {
                System.out.println("    [producer] Buffer full, waiting...");
                wait(); // Release lock and wait
            }

            buffer.add(value);
            System.out.println("    [producer] Produced: " + value +
                " | Buffer size: " + buffer.size());

            notify(); // Wake up waiting consumer
        }

        /**
         * Remove an item from the buffer.
         * Blocks if buffer is empty.
         */
        public synchronized int consume() throws InterruptedException {
            // MUST use while, not if! (spurious wakeups)
            while (buffer.isEmpty()) {
                System.out.println("    [consumer] Buffer empty, waiting...");
                wait(); // Release lock and wait
            }

            int value = buffer.poll();
            System.out.println("    [consumer] Consumed: " + value +
                " | Buffer size: " + buffer.size());

            notify(); // Wake up waiting producer
            return value;
        }
    }
}
