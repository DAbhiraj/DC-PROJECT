import java.io.*;
import java.util.*;

/**
 * StandaloneMapReduceSimulator: Simulates the MapReduce block matrix
 * multiplication
 * WITHOUT requiring Hadoop installation. Uses the same logic as the Hadoop
 * version
 * but runs locally in a single JVM.
 * 
 * This demonstrates the MapReduce paradigm:
 * MAP phase: Read each block file, emit (blockRowIdx, blockColIdx) -> block
 * data
 * REDUCE phase: For each key (i,j), receive A_block_i and B_block_j, multiply
 * them
 * 
 * Storage:
 * Matrix A: row-major, each file block = b rows of A
 * Matrix B: column-major, each file block = b columns of B
 * Each block file has metadata:
 * MATRIX_ID,BLOCK_INDEX,START,END,TOTAL_ROWS,TOTAL_COLS,BLOCK_SIZE
 * 
 * Usage: java StandaloneMapReduceSimulator [m] [n] [p] [blockSize]
 */
public class StandaloneMapReduceSimulator {

    // ==================== MAP PHASE ====================

    /**
     * Simulated Mapper: reads a block file and emits key-value pairs.
     * For A block i: emit (i, j) -> serialized A block, for all j in B blocks
     * For B block j: emit (i, j) -> serialized B block, for all i in A blocks
     */
    static Map<String, List<String>> mapPhase(String inputDirA, String inputDirB,
            int numABlocks, int numBBlocks) throws IOException {
        Map<String, List<String>> mapOutput = new LinkedHashMap<>();

        System.out.println("--- MAP PHASE ---");

        // Process A block files
        for (int blk = 0; blk < numABlocks; blk++) {
            String fileName = inputDirA + File.separator + "A_block_" + blk + ".txt";
            String serialized = readAndSerializeBlock(fileName);

            // Emit to all (blk, j) keys
            for (int j = 0; j < numBBlocks; j++) {
                String key = blk + "," + j;
                mapOutput.computeIfAbsent(key, k -> new ArrayList<>()).add(serialized);
                System.out.println("  MAP emit: key=" + key + " value=A_block_" + blk);
            }
        }

        // Process B block files
        for (int blk = 0; blk < numBBlocks; blk++) {
            String fileName = inputDirB + File.separator + "B_block_" + blk + ".txt";
            String serialized = readAndSerializeBlock(fileName);

            // Emit to all (i, blk) keys
            for (int i = 0; i < numABlocks; i++) {
                String key = i + "," + blk;
                mapOutput.computeIfAbsent(key, k -> new ArrayList<>()).add(serialized);
                System.out.println("  MAP emit: key=" + key + " value=B_block_" + blk);
            }
        }

        System.out.println("  Total map output keys: " + mapOutput.size());
        return mapOutput;
    }

    /**
     * Read a block file and serialize it into a string (same format as Hadoop
     * mapper).
     */
    static String readAndSerializeBlock(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String metaLine = br.readLine();
            String[] meta = metaLine.split(",");
            String matrixId = meta[0].trim();
            int blockIndex = Integer.parseInt(meta[1].trim());
            int startIndex = Integer.parseInt(meta[2].trim());
            int totalRows = Integer.parseInt(meta[4].trim());
            int totalCols = Integer.parseInt(meta[5].trim());
            int blockSize = Integer.parseInt(meta[6].trim());

            List<String> dataLines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty())
                    dataLines.add(line);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(matrixId).append(";");
            sb.append(blockIndex).append(";");
            sb.append(startIndex).append(";");
            sb.append(totalRows).append(";");
            sb.append(totalCols).append(";");
            sb.append(blockSize).append(";");
            for (int i = 0; i < dataLines.size(); i++) {
                if (i > 0)
                    sb.append("|");
                sb.append(dataLines.get(i));
            }
            return sb.toString();
        }
    }

    // ==================== SHUFFLE & SORT (implicit) ====================
    // In real MapReduce, shuffle groups values by key. Our map already does this.

    // ==================== REDUCE PHASE ====================

    /**
     * Simulated Reducer: for each key (i,j), receives A_block_i and B_block_j,
     * multiplies them to produce C_block[i][j].
     */
    static int[][] reducePhase(Map<String, List<String>> mapOutput, int m, int p, int blockSize) {
        int[][] C = new int[m][p];

        System.out.println("\n--- REDUCE PHASE ---");

        for (Map.Entry<String, List<String>> entry : mapOutput.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            System.out.println("  REDUCE key=" + key + " (received " + values.size() + " values)");

            int[][] aData = null;
            int[][] bData = null;
            int aStartRow = 0, bStartCol = 0;
            int n = 0;

            for (String serialized : values) {
                String[] parts = serialized.split(";", 7);
                String matrixId = parts[0];
                int blockIndex = Integer.parseInt(parts[1]);
                int startIndex = Integer.parseInt(parts[2]);
                int totalRows = Integer.parseInt(parts[3]);
                int totalCols = Integer.parseInt(parts[4]);
                int bs = Integer.parseInt(parts[5]);
                String dataStr = parts[6];
                String[] lines = dataStr.split("\\|");

                if ("A".equals(matrixId)) {
                    n = totalCols;
                    aData = new int[bs][n];
                    for (int i = 0; i < lines.length; i++) {
                        String[] vals = lines[i].split(",");
                        for (int j = 0; j < vals.length; j++)
                            aData[i][j] = Integer.parseInt(vals[j].trim());
                    }
                    aStartRow = startIndex;
                } else if ("B".equals(matrixId)) {
                    n = totalRows;
                    bData = new int[n][bs];
                    for (int col = 0; col < lines.length; col++) {
                        String[] vals = lines[col].split(",");
                        for (int row = 0; row < vals.length; row++)
                            bData[row][col] = Integer.parseInt(vals[row].trim());
                    }
                    bStartCol = startIndex;
                }
            }

            if (aData == null || bData == null) {
                System.err.println("  WARNING: Missing A or B data for key " + key);
                continue;
            }

            // Multiply blocks
            int bs = aData.length;
            for (int i = 0; i < bs; i++)
                for (int j = 0; j < bData[0].length; j++)
                    for (int k = 0; k < n; k++)
                        C[aStartRow + i][bStartCol + j] += aData[i][k] * bData[k][j];

            System.out.println("  REDUCE: computed C[" + aStartRow + ".." + (aStartRow + bs - 1) +
                    "][" + bStartCol + ".." + (bStartCol + bData[0].length - 1) + "]");
        }

        return C;
    }

    // ==================== VERIFICATION ====================

    static int[][] naiveMultiply(int[][] A, int[][] B) {
        int m = A.length, n = A[0].length, p = B[0].length;
        int[][] C = new int[m][p];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < n; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    static int[][] readFullA(String dir, int m, int n, int blockSize) throws IOException {
        int[][] A = new int[m][n];
        int numBlocks = m / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            try (BufferedReader br = new BufferedReader(new FileReader(
                    dir + File.separator + "A_block_" + blk + ".txt"))) {
                br.readLine(); // skip metadata
                int startRow = blk * blockSize;
                for (int i = 0; i < blockSize; i++) {
                    String[] vals = br.readLine().split(",");
                    for (int j = 0; j < n; j++)
                        A[startRow + i][j] = Integer.parseInt(vals[j].trim());
                }
            }
        }
        return A;
    }

    static int[][] readFullB(String dir, int n, int p, int blockSize) throws IOException {
        int[][] B = new int[n][p];
        int numBlocks = p / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            try (BufferedReader br = new BufferedReader(new FileReader(
                    dir + File.separator + "B_block_" + blk + ".txt"))) {
                br.readLine(); // skip metadata
                int startCol = blk * blockSize;
                for (int col = 0; col < blockSize; col++) {
                    String[] vals = br.readLine().split(",");
                    for (int row = 0; row < n; row++)
                        B[row][startCol + col] = Integer.parseInt(vals[row].trim());
                }
            }
        }
        return B;
    }

    static void printMatrix(int[][] matrix, String name) {
        System.out.println("Matrix " + name + " (" + matrix.length + "x" + matrix[0].length + "):");
        for (int[] row : matrix) {
            StringBuilder sb = new StringBuilder("  ");
            for (int j = 0; j < row.length; j++) {
                if (j > 0)
                    sb.append("\t");
                sb.append(row[j]);
            }
            System.out.println(sb.toString());
        }
    }

    static boolean matricesEqual(int[][] A, int[][] B) {
        if (A.length != B.length || A[0].length != B[0].length)
            return false;
        for (int i = 0; i < A.length; i++)
            for (int j = 0; j < A[0].length; j++)
                if (A[i][j] != B[i][j])
                    return false;
        return true;
    }

    // ==================== MAIN ====================

    public static void main(String[] args) throws Exception {
        int m = 4, n = 4, p = 4, blockSize = 2;

        if (args.length >= 4) {
            m = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
            p = Integer.parseInt(args[2]);
            blockSize = Integer.parseInt(args[3]);
        }

        if (m % blockSize != 0 || n % blockSize != 0 || p % blockSize != 0) {
            System.err.println("ERROR: m, n, p must be divisible by blockSize!");
            System.exit(1);
        }

        System.out.println("==========================================================");
        System.out.println(" Block Matrix Multiplication - MapReduce Simulation");
        System.out.println("==========================================================");
        System.out.println("A[" + m + "x" + n + "] x B[" + n + "x" + p + "] = C[" + m + "x" + p + "]");
        System.out.println("Block size: " + blockSize);
        System.out.println("A storage: row-major, " + (m / blockSize) + " blocks of " + blockSize + " rows each");
        System.out.println("B storage: column-major, " + (p / blockSize) + " blocks of " + blockSize + " columns each");
        System.out.println();

        // Step 1: Generate block files (simulating HDFS storage)
        System.out.println("=== Step 1: Generate input block files (simulated HDFS) ===");
        String dirA = "input_A";
        String dirB = "input_B";

        // Use MatrixGenerator to create block files
        MatrixGenerator.generateMatrixA(m, n, blockSize, dirA);
        MatrixGenerator.generateMatrixB(n, p, blockSize, dirB);

        // Read and display full matrices
        int[][] fullA = readFullA(dirA, m, n, blockSize);
        int[][] fullB = readFullB(dirB, n, p, blockSize);
        System.out.println();
        printMatrix(fullA, "A");
        printMatrix(fullB, "B");

        int numABlocks = m / blockSize;
        int numBBlocks = p / blockSize;

        // Step 2: MAP phase
        System.out.println("\n=== Step 2: MAP Phase ===");
        Map<String, List<String>> mapOutput = mapPhase(dirA, dirB, numABlocks, numBBlocks);

        // Step 3: SHUFFLE & SORT (already done - grouped by key)
        System.out.println("\n=== Step 3: SHUFFLE & SORT (grouping by key) ===");
        System.out.println("  Keys after shuffle: " + mapOutput.keySet());

        // Step 4: REDUCE phase
        System.out.println("\n=== Step 4: REDUCE Phase ===");
        int[][] resultC = reducePhase(mapOutput, m, p, blockSize);

        // Step 5: Output
        System.out.println("\n=== Step 5: OUTPUT ===");
        printMatrix(resultC, "C (MapReduce result)");

        // Step 6: Verification
        System.out.println("\n=== Step 6: VERIFICATION ===");
        int[][] expectedC = naiveMultiply(fullA, fullB);
        printMatrix(expectedC, "C (expected)");

        if (matricesEqual(resultC, expectedC)) {
            System.out.println("\n*** VERIFICATION PASSED! MapReduce result matches expected output ***");
        } else {
            System.out.println("\n*** VERIFICATION FAILED! Results do not match ***");
        }

        System.out.println("\n==========================================================");
        System.out.println(" MapReduce Simulation Complete!");
        System.out.println("==========================================================");
    }
}
