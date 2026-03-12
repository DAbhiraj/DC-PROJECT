import java.util.concurrent.*;

/**
 * SocketMatrixMultiply: Main entry point that launches worker nodes and master
 * node
 * in a single JVM for easy testing and demonstration.
 * 
 * This simulates a distributed system by running workers on different ports
 * in separate threads, then running the master node to coordinate.
 * 
 * Usage: java SocketMatrixMultiply [m] [n] [p] [blockSize] [numWorkers]
 * Default: m=4, n=4, p=4, blockSize=2, numWorkers=2
 */
public class SocketMatrixMultiply {

    public static void main(String[] args) throws Exception {
        int m = 4, n = 4, p = 4, blockSize = 2, numWorkers = 2;

        if (args.length >= 1)
            m = Integer.parseInt(args[0]);
        if (args.length >= 2)
            n = Integer.parseInt(args[1]);
        if (args.length >= 3)
            p = Integer.parseInt(args[2]);
        if (args.length >= 4)
            blockSize = Integer.parseInt(args[3]);
        if (args.length >= 5)
            numWorkers = Integer.parseInt(args[4]);

        // Validate
        if (m % blockSize != 0 || n % blockSize != 0 || p % blockSize != 0) {
            System.err.println("ERROR: m, n, p must all be divisible by blockSize!");
            System.err.println("  m=" + m + ", n=" + n + ", p=" + p + ", blockSize=" + blockSize);
            System.exit(1);
        }

        System.out.println("======================================================");
        System.out.println(" Distributed Block Matrix Multiplication");
        System.out.println(" Using Socket Programming");
        System.out.println("======================================================");
        System.out.println("A[" + m + "x" + n + "] x B[" + n + "x" + p + "] = C[" + m + "x" + p + "]");
        System.out.println("Block size: " + blockSize);
        System.out.println("Number of workers: " + numWorkers);
        System.out.println("A blocks (row groups): " + (m / blockSize));
        System.out.println("B blocks (col groups): " + (p / blockSize));
        System.out.println("Total multiplication tasks: " + ((m / blockSize) * (p / blockSize)));
        System.out.println("======================================================");
        System.out.println();

        // Assign ports to workers
        int basePort = 5001;
        int[] workerPorts = new int[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            workerPorts[i] = basePort + i;
        }

        // Launch worker threads
        ExecutorService workerExecutor = Executors.newFixedThreadPool(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            final int port = workerPorts[i];
            workerExecutor.submit(() -> {
                WorkerNode worker = new WorkerNode(port);
                worker.start();
            });
        }

        // Give workers time to start
        System.out.println("Starting " + numWorkers + " worker nodes...");
        Thread.sleep(2000);

        // Launch master
        System.out.println("Starting master node...\n");
        MasterNode master = new MasterNode(m, n, p, blockSize, workerPorts);
        master.execute();

        // Shutdown
        workerExecutor.shutdown();
        workerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        workerExecutor.shutdownNow();

        System.out.println("\n=== All done! ===");
        System.exit(0);
    }
}
