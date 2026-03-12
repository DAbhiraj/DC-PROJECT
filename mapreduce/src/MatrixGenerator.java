import java.io.*;
import java.util.Random;

/**
 * Generates block files for Matrix A (row-major) and Matrix B (column-major).
 * 
 * Block Metadata Format (first line of each block file):
 * MATRIX_ID,BLOCK_INDEX,START_INDEX,END_INDEX,TOTAL_ROWS,TOTAL_COLS,BLOCK_SIZE
 * 
 * For Matrix A blocks: START_INDEX/END_INDEX refer to row indices
 * For Matrix B blocks: START_INDEX/END_INDEX refer to column indices
 */
public class MatrixGenerator {

    /**
     * Generate Matrix A in row-major block files.
     * Each file block contains b rows of A.
     * File: A_block_<blockIndex>.txt
     */
    public static void generateMatrixA(int m, int n, int blockSize, String outputDir) throws IOException {
        Random rand = new Random(42);
        new File(outputDir).mkdirs();

        int numBlocks = m / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            int startRow = blk * blockSize;
            int endRow = startRow + blockSize - 1;
            String fileName = outputDir + File.separator + "A_block_" + blk + ".txt";

            try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
                // Metadata line
                pw.println("A," + blk + "," + startRow + "," + endRow + "," + m + "," + n + "," + blockSize);

                // Data: b rows, each with n values (row-major)
                for (int i = startRow; i <= endRow; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < n; j++) {
                        if (j > 0)
                            sb.append(",");
                        sb.append(rand.nextInt(10));
                    }
                    pw.println(sb.toString());
                }
            }
            System.out.println("Generated: " + fileName);
        }
    }

    /**
     * Generate Matrix B in column-major block files.
     * Each file block contains b columns of B.
     * File: B_block_<blockIndex>.txt
     * 
     * Storage: each line represents one column of B (n values top-to-bottom).
     */
    public static void generateMatrixB(int n, int p, int blockSize, String outputDir) throws IOException {
        Random rand = new Random(123);
        new File(outputDir).mkdirs();

        int numBlocks = p / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            int startCol = blk * blockSize;
            int endCol = startCol + blockSize - 1;
            String fileName = outputDir + File.separator + "B_block_" + blk + ".txt";

            try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
                // Metadata line
                pw.println("B," + blk + "," + startCol + "," + endCol + "," + n + "," + p + "," + blockSize);

                // Data: b columns, each stored as a line with n values (column-major)
                // Each line = one column of B
                for (int col = startCol; col <= endCol; col++) {
                    StringBuilder sb = new StringBuilder();
                    for (int row = 0; row < n; row++) {
                        if (row > 0)
                            sb.append(",");
                        sb.append(rand.nextInt(10));
                    }
                    pw.println(sb.toString());
                }
            }
            System.out.println("Generated: " + fileName);
        }
    }

    /**
     * Read all blocks of A back into a full matrix for verification.
     */
    public static int[][] readFullMatrixA(int m, int n, int blockSize, String inputDir) throws IOException {
        int[][] A = new int[m][n];
        int numBlocks = m / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            String fileName = inputDir + File.separator + "A_block_" + blk + ".txt";
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                br.readLine(); // skip metadata
                int startRow = blk * blockSize;
                for (int i = 0; i < blockSize; i++) {
                    String[] vals = br.readLine().split(",");
                    for (int j = 0; j < n; j++) {
                        A[startRow + i][j] = Integer.parseInt(vals[j].trim());
                    }
                }
            }
        }
        return A;
    }

    /**
     * Read all blocks of B back into a full matrix for verification.
     */
    public static int[][] readFullMatrixB(int n, int p, int blockSize, String inputDir) throws IOException {
        int[][] B = new int[n][p];
        int numBlocks = p / blockSize;
        for (int blk = 0; blk < numBlocks; blk++) {
            String fileName = inputDir + File.separator + "B_block_" + blk + ".txt";
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                br.readLine(); // skip metadata
                int startCol = blk * blockSize;
                for (int col = 0; col < blockSize; col++) {
                    String[] vals = br.readLine().split(",");
                    for (int row = 0; row < n; row++) {
                        B[row][startCol + col] = Integer.parseInt(vals[row].trim());
                    }
                }
            }
        }
        return B;
    }

    /**
     * Naive matrix multiplication for verification.
     */
    public static int[][] multiply(int[][] A, int[][] B, int m, int n, int p) {
        int[][] C = new int[m][p];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < n; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    public static void printMatrix(int[][] matrix, String name) {
        System.out.println("Matrix " + name + ":");
        for (int[] row : matrix) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < row.length; j++) {
                if (j > 0)
                    sb.append("\t");
                sb.append(row[j]);
            }
            System.out.println(sb.toString());
        }
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        int m = 4, n = 4, p = 4, blockSize = 2;

        if (args.length >= 4) {
            m = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
            p = Integer.parseInt(args[2]);
            blockSize = Integer.parseInt(args[3]);
        }

        System.out.println("=== Matrix Generator ===");
        System.out.println("A: " + m + "x" + n + ", B: " + n + "x" + p + ", Block size: " + blockSize);

        String dirA = "input_A";
        String dirB = "input_B";

        generateMatrixA(m, n, blockSize, dirA);
        generateMatrixB(n, p, blockSize, dirB);

        // Verification
        int[][] A = readFullMatrixA(m, n, blockSize, dirA);
        int[][] B = readFullMatrixB(n, p, blockSize, dirB);
        int[][] C = multiply(A, B, m, n, p);

        printMatrix(A, "A");
        printMatrix(B, "B");
        printMatrix(C, "C = A x B (expected)");
    }
}
