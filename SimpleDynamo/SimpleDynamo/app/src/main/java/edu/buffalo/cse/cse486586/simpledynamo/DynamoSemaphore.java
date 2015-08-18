package edu.buffalo.cse.cse486586.simpledynamo;

/**
 * Created by dheeraj on 4/14/15.
 */
public class DynamoSemaphore {

    private int value;

    public DynamoSemaphore(int init) {
        if (init < 0) {
            init = 0;
        }
        this.value = init;
    }

    // this is where we acquire the resources
    public synchronized void acquire() {
        while (this.value == 0) {
            try {
                System.out.println ("SEMAPHORE WAIT");
                wait();
            } catch (InterruptedException e) {
                System.out.println("Interrupted Exception in DynamoSemaphore's acquire call");
            }
        }
        System.out.println ("SEMAPHORE ACQUIRED");
        this.value--;
    }

    // this is where we release the resources
    public synchronized void release() {
        System.out.println ("SEMAPHORE RELEASED");
        this.value++;
        notify();
    }

}
