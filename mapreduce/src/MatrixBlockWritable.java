import org.apache.hadoop.io.Writable;
import java.io.*;

/**
 * Custom Writable to hold a matrix block with metadata.
 * Carries: matrixId (A or B), blockIndex, startIndex, dimensions, and the data
 * array.
 */
public class MatrixBlockWritable implements Writable {
    private String matrixId; // "A" or "B"
    private int blockIndex;
    private int startIndex; // startRow for A, startCol for B
    private int totalRows;
    private int totalCols;
    private int blockSize;
    private int[][] data; // For A: blockSize x n, For B: n x blockSize

    public MatrixBlockWritable() {
    }

    public MatrixBlockWritable(String matrixId, int blockIndex, int startIndex,
            int totalRows, int totalCols, int blockSize, int[][] data) {
        this.matrixId = matrixId;
        this.blockIndex = blockIndex;
        this.startIndex = startIndex;
        this.totalRows = totalRows;
        this.totalCols = totalCols;
        this.blockSize = blockSize;
        this.data = data;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(matrixId);
        out.writeInt(blockIndex);
        out.writeInt(startIndex);
        out.writeInt(totalRows);
        out.writeInt(totalCols);
        out.writeInt(blockSize);
        out.writeInt(data.length);
        out.writeInt(data[0].length);
        for (int[] row : data)
            for (int val : row)
                out.writeInt(val);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        matrixId = in.readUTF();
        blockIndex = in.readInt();
        startIndex = in.readInt();
        totalRows = in.readInt();
        totalCols = in.readInt();
        blockSize = in.readInt();
        int rows = in.readInt();
        int cols = in.readInt();
        data = new int[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                data[i][j] = in.readInt();
    }

    // Getters
    public String getMatrixId() {
        return matrixId;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getTotalCols() {
        return totalCols;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int[][] getData() {
        return data;
    }

    @Override
    public String toString() {
        return matrixId + ",block=" + blockIndex + ",start=" + startIndex;
    }
}
