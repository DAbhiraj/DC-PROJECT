import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.*;

/**
 * Reducer for Block Matrix Multiplication.
 * 
 * Key: "i,j" -> identifies result block C[i,j] (block row i, block col j)
 * Values: serialized A block i data AND B block j data.
 * 
 * For each (i,j) key, we receive:
 * - The A sub-block (blockSize rows x n cols, row-major)
 * - The B sub-block (n rows x blockSize cols, column-major stored as columns)
 * 
 * We compute C_block[i][j] = A_block_i * B_block_j
 * which is a blockSize x blockSize sub-matrix of C.
 * 
 * Output: key = block identifier, value = result sub-matrix rows
 */
public class BlockMatrixReducer extends Reducer<Text, Text, Text, Text> {

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {

        int[][] aData = null;
        int[][] bData = null;
        int aStartRow = 0, bStartCol = 0;
        int blockSize = 0;
        int n = 0; // shared dimension

        // Collect A and B blocks
        // There might be multiple A-sub-blocks and B-sub-blocks if n > blockSize
        // but in our scheme, each A block contains ALL n columns, and each B block
        // contains ALL n rows. So we get exactly one A block and one B block per key.
        List<int[][]> aBlocks = new ArrayList<>();
        List<int[][]> bBlocks = new ArrayList<>();
        List<Integer> aStarts = new ArrayList<>();
        List<Integer> bStarts = new ArrayList<>();

        for (Text val : values) {
            String serialized = val.toString();
            String[] parts = serialized.split(";", 7);
            String matrixId = parts[0];
            int blockIndex = Integer.parseInt(parts[1]);
            int startIndex = Integer.parseInt(parts[2]);
            int totalRows = Integer.parseInt(parts[3]);
            int totalCols = Integer.parseInt(parts[4]);
            blockSize = Integer.parseInt(parts[5]);
            String dataStr = parts[6];

            String[] lines = dataStr.split("\\|");

            if ("A".equals(matrixId)) {
                // A block: blockSize rows, each with n=totalCols values
                n = totalCols;
                int[][] block = new int[blockSize][n];
                for (int i = 0; i < lines.length; i++) {
                    String[] vals = lines[i].split(",");
                    for (int j = 0; j < vals.length; j++) {
                        block[i][j] = Integer.parseInt(vals[j].trim());
                    }
                }
                aData = block;
                aStartRow = startIndex;
            } else if ("B".equals(matrixId)) {
                // B block: blockSize columns, each stored as line with n values
                n = totalRows;
                // Convert column-major to row-major: bData[row][col_within_block]
                int[][] block = new int[n][blockSize];
                for (int col = 0; col < lines.length; col++) {
                    String[] vals = lines[col].split(",");
                    for (int row = 0; row < vals.length; row++) {
                        block[row][col] = Integer.parseInt(vals[row].trim());
                    }
                }
                bData = block;
                bStartCol = startIndex;
            }
        }

        if (aData == null || bData == null) {
            return; // Missing data - skip
        }

        // Multiply: C_sub = A_block * B_block
        // A_block: blockSize x n, B_block: n x blockSize => C_sub: blockSize x
        // blockSize
        int[][] cBlock = new int[blockSize][blockSize];
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                int sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += aData[i][k] * bData[k][j];
                }
                cBlock[i][j] = sum;
            }
        }

        // Output result
        // Format: each row of the result sub-block
        for (int i = 0; i < blockSize; i++) {
            StringBuilder sb = new StringBuilder();
            // Global row and column indices
            int globalRow = aStartRow + i;
            sb.append("Row ").append(globalRow).append(", Cols ").append(bStartCol)
                    .append("-").append(bStartCol + blockSize - 1).append(": ");
            for (int j = 0; j < blockSize; j++) {
                if (j > 0)
                    sb.append(",");
                sb.append(cBlock[i][j]);
            }
            context.write(new Text(key.toString()), new Text(sb.toString()));
        }
    }
}
