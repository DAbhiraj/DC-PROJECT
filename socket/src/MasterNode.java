import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MasterNode: Coordinates distributed block matrix multiplication via sockets.
 * 
 * Workflow:
 * 1. Generate matrix blocks for A (row-major) and B (column-major)
 * 2. Create task list: for each (A_block_i, B_block_j) pair, compute C[i,j]
 * 3. Distribute tasks to worker nodes via TCP sockets
 * 4. Collect results and assemble the final matrix C
 * 5. Verify against naive multiplication
 * 
 * Usage: java MasterNode <m> <n>
 * <p>
 * <blockSize> <workerPort1> [workerPort2] ...
 * Example: java MasterNode 4 4 4 2 5001 5002
 */
public class MasterNode {

    private int m, n, p, blockSize;
    private int[] workerPorts;
    private MatrixBlock[] aBlocks;
    private MatrixBlock[] bBlocks;
    private int[][] resultC;

    public MasterNode(int m, int n, int p, int blockSize, int[] workerPorts) {
        this.m = m;
        this.n = n;
        this.p = p;
        this.blockSize = blockSize;
        this.workerPorts = workerPorts;
    }

    public void execute() throws Exception {
        System.out.println("============================================");
        System.out.println(" Block Matrix Multiplication - Socket Based");
        System.out.println("============================================");
        System.out.println("A: " + m + "x" + n + ", B: " + n + "x" + p + ", Block size: " + blockSize);
        System.out.println("Workers: " + workerPorts.length + " (ports: " + Arrays.toString(workerPorts) + ")");
        System.out.println();

        // Step 1: Generate blocks
        System.out.println("=== Step 1: Generating matrix blocks ===");
        aBlocks = MatrixUtils.generateMatrixABlocks(m, n, blockSize);
        bBlocks = MatrixUtils.generateMatrixBBlocks(n, p, blockSize);

        // Write block files to disk for reference
        MatrixUtils.writeBlockFiles(aBlocks, "input_A");
        MatrixUtils.writeBlockFiles(bBlocks, "input_B");

        System.out.println("Generated " + aBlocks.length + " A-blocks and " + bBlocks.length + " B-blocks");
        System.out.println("Block files written to input_A/ and input_B/");

        // Show input matrices
        int[][] fullA = MatrixUtils.reconstructMatrixA(aBlocks, m, n);
        int[][] fullB = MatrixUtils.reconstructMatrixB(bBlocks, n, p);
        MatrixUtils.printMatrix(fullA, "A");
        MatrixUtils.printMatrix(fullB, "B");

        // Step 2: Create task list (all pairs of A-block and B-block)
        System.out.println("\n=== Step 2: Creating task list ===");
        List<int[]> tasks = new ArrayList<>();
        for (int i = 0; i < aBlocks.length; i++) {
            for (int j = 0; j < bBlocks.length; j++) {
                tasks.add(new int[] { i, j });
            }
        }
        System.out.println("Total tasks: " + tasks.size() +
                " (" + aBlocks.length + " A-blocks x " + bBlocks.length + " B-blocks)");

        // Step 3: Distribute tasks to workers
        System.out.println("\n=== Step 3: Distributing tasks to workers ===");
        resultC = new int[m][p];

        long startTime = System.currentTimeMillis();

        // Use thread pool to send tasks to workers in parallel
        ExecutorService executor = Executors.newFixedThreadPool(workerPorts.length);
        List<Future<Void>> futures = new ArrayList<>();

        // Round-robin assignment of tasks to workers
        Map<Integer, List<int[]>> workerTasks = new HashMap<>();
        for (int w = 0; w < workerPorts.length; w++) {
            workerTasks.put(w, new ArrayList<>());
        }
        for (int t = 0; t < tasks.size(); t++) {
            int workerIdx = t % workerPorts.length;
            workerTasks.get(workerIdx).add(tasks.get(t));
        }

        for (int w = 0; w < workerPorts.length; w++) {
            final int workerIdx = w;
            final int port = workerPorts[w];
            final List<int[]> assignedTasks = workerTasks.get(w);

            futures.add(executor.submit(() -> {
                sendTasksToWorker(port, assignedTasks, workerIdx);
                return null;
            }));
        }

        // Wait for all workers to finish
        for (Future<Void> f : futures) {
            try {
                f.get(120, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.err.println("Worker timed out!");
            }
        }
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        System.out.println("\nAll tasks completed in " + (endTime - startTime) + "ms");

        // Step 4: Show result
        System.out.println("\n=== Step 4: Result Matrix C = A x B ===");
        MatrixUtils.printMatrix(resultC, "C (distributed)");

        // Step 5: Verify
        System.out.println("\n=== Step 5: Verification ===");
        int[][] expectedC = MatrixUtils.naiveMultiply(fullA, fullB);
        MatrixUtils.printMatrix(expectedC, "C (expected)");

        if (MatrixUtils.matricesEqual(resultC, expectedC)) {
            System.out.println("\n*** VERIFICATION PASSED: Distributed result matches naive multiplication! ***");
        } else {
            System.out.println("\n*** VERIFICATION FAILED: Results do not match! ***");
        }
    }

    private void sendTasksToWorker(int port, List<int[]> tasks, int workerIdx) {
        System.out.println("[Master] Connecting to worker " + workerIdx + " on port " + port +
                " (" + tasks.size() + " tasks)");

        int retries = 10;
        Socket socket = null;

        // Retry connection (worker might not be ready yet)
        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                socket = new Socket("localhost", port);
                break;
            } catch (IOException e) {
                System.out.println("[Master] Worker " + workerIdx + " not ready, retrying in 1s... (" +
                        (attempt + 1) + "/" + retries + ")");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }

        if (socket == null) {
            System.err.println("[Master] Failed to connect to worker " + workerIdx + " on port " + port);
            return;
        }

        try {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.flush();
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            for (int[] task : tasks) {
                int aIdx = task[0];
                int bIdx = task[1];

                System.out.println("[Master -> Worker" + workerIdx + "] Sending task: A_block_" +
                        aIdx + " x B_block_" + bIdx);

                // Send MULTIPLY command
                oos.writeObject("MULTIPLY");
                oos.writeObject(aBlocks[aIdx]);
                oos.writeObject(bBlocks[bIdx]);
                oos.flush();

                // Receive result
                String response = (String) ois.readObject();
                if ("RESULT".equals(response)) {
                    MatrixBlock resultBlock = (MatrixBlock) ois.readObject();
                    int startRow = ois.readInt();
                    int startCol = ois.readInt();
                    int[][] blockResult = resultBlock.getData();

                    // Place into result matrix C
                    synchronized (resultC) {
                        for (int i = 0; i < blockResult.length; i++) {
                            for (int j = 0; j < blockResult[0].length; j++) {
                                resultC[startRow + i][startCol + j] = blockResult[i][j];
                            }
                        }
                    }

                    System.out.println("[Master <- Worker" + workerIdx + "] Got result for C[" +
                            startRow + ".." + (startRow + blockSize - 1) + "][" +
                            startCol + ".." + (startCol + blockSize - 1) + "]");
                }
            }

            // Send SHUTDOWN
            oos.writeObject("SHUTDOWN");
            oos.flush();
            ois.readObject(); // ACK

            oos.close();
            ois.close();
        } catch (Exception e) {
            System.err.println("[Master] Error communicating with worker " + workerIdx + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                /* ignore */ }
        }
    }

    public static void main(String[] args) throws Exception {
        int m = 4, n = 4, p = 4, blockSize = 2;
        int[] ports = { 5001, 5002 };

        if (args.length >= 4) {
            m = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
            p = Integer.parseInt(args[2]);
            blockSize = Integer.parseInt(args[3]);
        }

        if (args.length >= 5) {
            ports = new int[args.length - 4];
            for (int i = 4; i < args.length; i++) {
                ports[i - 4] = Integer.parseInt(args[i]);
            }
        }

        MasterNode master = new MasterNode(m, n, p, blockSize, ports);
        master.execute();
    }
}
