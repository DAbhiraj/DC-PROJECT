import java.io.*;
import java.net.*;

/**
 * WorkerNode: Listens on a specified port, receives a pair of matrix blocks
 * (A block and B block), performs block multiplication, and sends result back.
 * 
 * Protocol:
 * 1. Receive MatrixBlock for A sub-block
 * 2. Receive MatrixBlock for B sub-block
 * 3. Compute C_sub = A_block * B_block
 * 4. Send back result as int[][] plus metadata (startRow, startCol)
 * 5. Wait for next task or SHUTDOWN signal
 * 
 * Usage: java WorkerNode <port>
 */
public class WorkerNode {
    private int port;
    private volatile boolean running = true;

    public WorkerNode(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("[Worker:" + port + "] Starting worker on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(60000); // 60 second timeout for accept

            while (running) {
                System.out.println("[Worker:" + port + "] Waiting for connection...");
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    System.out.println("[Worker:" + port + "] Accept timed out, checking if still running...");
                    continue;
                }

                System.out.println("[Worker:" + port + "] Connected to master: " + socket.getRemoteSocketAddress());

                try {
                    handleConnection(socket);
                } catch (Exception e) {
                    System.err.println("[Worker:" + port + "] Error handling connection: " + e.getMessage());
                } finally {
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.err.println("[Worker:" + port + "] Server error: " + e.getMessage());
        }

        System.out.println("[Worker:" + port + "] Shutting down.");
    }

    private void handleConnection(Socket socket) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        oos.flush();

        while (true) {
            // Read command
            String command;
            try {
                command = (String) ois.readObject();
            } catch (EOFException e) {
                System.out.println("[Worker:" + port + "] Connection closed by master.");
                break;
            }

            if ("SHUTDOWN".equals(command)) {
                System.out.println("[Worker:" + port + "] Received SHUTDOWN command.");
                running = false;
                oos.writeObject("ACK");
                oos.flush();
                break;
            }

            if ("MULTIPLY".equals(command)) {
                System.out.println("[Worker:" + port + "] Received MULTIPLY command.");

                // Receive A block
                MatrixBlock aBlock = (MatrixBlock) ois.readObject();
                System.out.println("[Worker:" + port + "] Received A block: " + aBlock);

                // Receive B block
                MatrixBlock bBlock = (MatrixBlock) ois.readObject();
                System.out.println("[Worker:" + port + "] Received B block: " + bBlock);

                // Compute multiplication
                long startTime = System.currentTimeMillis();
                int[][] result = MatrixUtils.multiplyBlocks(aBlock.getData(), bBlock.getData());
                long endTime = System.currentTimeMillis();

                System.out.println("[Worker:" + port + "] Multiplication done in " +
                        (endTime - startTime) + "ms. Result: " +
                        result.length + "x" + result[0].length);

                // Send back result with metadata
                int startRow = aBlock.getStartIndex();
                int startCol = bBlock.getStartIndex();
                int blockSize = aBlock.getBlockSize();

                MatrixBlock resultBlock = new MatrixBlock("C", -1, startRow, startRow + blockSize - 1,
                        aBlock.getTotalRows(), bBlock.getTotalCols(), blockSize, result);
                // Store startCol in totalCols field for the result
                resultBlock.setTotalCols(startCol);

                oos.writeObject("RESULT");
                oos.writeObject(resultBlock);
                oos.writeInt(startRow);
                oos.writeInt(startCol);
                oos.flush();

                System.out.println("[Worker:" + port + "] Sent result for C[" +
                        startRow + ".." + (startRow + blockSize - 1) + "][" +
                        startCol + ".." + (startCol + blockSize - 1) + "]");
            }
        }

        ois.close();
        oos.close();
    }

    public static void main(String[] args) {
        int port = 5001;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        WorkerNode worker = new WorkerNode(port);
        worker.start();
    }
}
