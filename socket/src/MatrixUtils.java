import java.io.*;
import java.util.Random;

/**
 * Utility class for matrix operations:
 * - Generate matrices A (row-major blocks) and B (column-major blocks)
 * - Read/write block files
 * - Block multiplication
 * - Verification via naive multiplication
 */
public class MatrixUtils {

    /**
     * Generate Matrix A as blocks. Each block has blockSize rows, n columns.
     * Returns array of MatrixBlock objects.
     */
    public static MatrixBlock[] generateMatrixABlocks(int m, int n, int blockSize) {
        Random rand = new Random(42);
        int numBlocks = m / blockSize;
        MatrixBlock[] blocks = new MatrixBlock[numBlocks];

        for (int blk = 0; blk < numBlocks; blk++) {
            int startRow = blk * blockSize;
            int endRow = startRow + blockSize - 1;
            int[][] data = new int[blockSize][n];

            for (int i = 0; i < blockSize; i++)
                for (int j = 0; j < n; j++)
                    data[i][j] = rand.nextInt(10);

            blocks[blk] = new MatrixBlock("A", blk, startRow, endRow, m, n, blockSize, data);
        }
        return blocks;
    }

    /**
     * Generate Matrix B as blocks. Each block has blockSize columns, n rows.
     * Stored column-major: data[col_within_block][row] for each column.
     * But we convert to standard row-major for easier processing:
     * data[row][col_within_block]
     */
    public static MatrixBlock[] generateMatrixBBlocks(int n, int p, int blockSize) {
        Random rand = new Random(123);
        int numBlocks = p / blockSize;
        MatrixBlock[] blocks = new MatrixBlock[numBlocks];

        for (int blk = 0; blk < numBlocks; blk++) {
            int startCol = blk * blockSize;
            int endCol = startCol + blockSize - 1;

            // Generate in column-major then store as row-major [n][blockSize]
            int[][] data = new int[n][blockSize];
            for (int col = 0; col < blockSize; col++)
                for (int row = 0; row < n; row++)
                    data[row][col] = rand.nextInt(10);

            blocks[blk] = new MatrixBlock("B", blk, startCol, endCol, n, p, blockSize, data);
        }
        return blocks;
    }

    /**
     * Write block files to disk.
     * A blocks: row-major, metadata + blockSize lines of n values
     * B blocks: column-major, metadata + blockSize lines of n values (each line =
     * one column)
     */
    public static void writeBlockFiles(MatrixBlock[] blocks, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        for (MatrixBlock block : blocks) {
            String fileName = outputDir + File.separator +
                    block.getMatrixId() + "_block_" + block.getBlockIndex() + ".txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
                pw.println(block.getMatrixId() + "," + block.getBlockIndex() + "," +
                        block.getStartIndex() + "," + block.getEndIndex() + "," +
                        block.getTotalRows() + "," + block.getTotalCols() + "," +
                        block.getBlockSize());

                int[][] data = block.getData();
                if ("A".equals(block.getMatrixId())) {
                    // Row-major: each line is a row
                    for (int[] row : data) {
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < row.length; j++) {
                            if (j > 0)
                                sb.append(",");
                            sb.append(row[j]);
                        }
                        pw.println(sb.toString());
                    }
                } else {
                    // Column-major: each line is a column (n values)
                    int n = data.length;
                    int bSize = data[0].length;
                    for (int col = 0; col < bSize; col++) {
                        StringBuilder sb = new StringBuilder();
                        for (int row = 0; row < n; row++) {
                            if (row > 0)
                                sb.append(",");
                            sb.append(data[row][col]);
                        }
                        pw.println(sb.toString());
                    }
                }
            }
        }
    }

    /**
     * Multiply A block (blockSize x n) by B block (n x blockSize).
     * Result: blockSize x blockSize sub-matrix.
     */
    public static int[][] multiplyBlocks(int[][] aBlock, int[][] bBlock) {
        int bSize = aBlock.length;
        int n = aBlock[0].length;
        int bCols = bBlock[0].length;
        int[][] result = new int[bSize][bCols];

        for (int i = 0; i < bSize; i++)
            for (int j = 0; j < bCols; j++)
                for (int k = 0; k < n; k++)
                    result[i][j] += aBlock[i][k] * bBlock[k][j];

        return result;
    }

    /**
     * Reconstruct full matrix A from blocks.
     */
    public static int[][] reconstructMatrixA(MatrixBlock[] blocks, int m, int n) {
        int[][] A = new int[m][n];
        for (MatrixBlock block : blocks) {
            int startRow = block.getStartIndex();
            int[][] data = block.getData();
            for (int i = 0; i < data.length; i++)
                System.arraycopy(data[i], 0, A[startRow + i], 0, n);
        }
        return A;
    }

    /**
     * Reconstruct full matrix B from blocks.
     */
    public static int[][] reconstructMatrixB(MatrixBlock[] blocks, int n, int p) {
        int[][] B = new int[n][p];
        for (MatrixBlock block : blocks) {
            int startCol = block.getStartIndex();
            int[][] data = block.getData();
            for (int row = 0; row < n; row++)
                for (int col = 0; col < data[0].length; col++)
                    B[row][startCol + col] = data[row][col];
        }
        return B;
    }

    /**
     * Naive full matrix multiplication for verification.
     */
    public static int[][] naiveMultiply(int[][] A, int[][] B) {
        int m = A.length;
        int n = A[0].length;
        int p = B[0].length;
        int[][] C = new int[m][p];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < p; j++)
                for (int k = 0; k < n; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }

    /**
     * Print a matrix.
     */
    public static void printMatrix(int[][] matrix, String name) {
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

    /**
     * Compare two matrices.
     */
    public static boolean matricesEqual(int[][] A, int[][] B) {
        if (A.length != B.length || A[0].length != B[0].length)
            return false;
        for (int i = 0; i < A.length; i++)
            for (int j = 0; j < A[0].length; j++)
                if (A[i][j] != B[i][j])
                    return false;
        return true;
    }
}
