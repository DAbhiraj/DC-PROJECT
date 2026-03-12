import java.io.Serializable;

/**
 * Represents a block of a matrix with metadata.
 * Used for transferring blocks between Master and Worker nodes.
 * 
 * Metadata includes:
 * - matrixId: "A" or "B"
 * - blockIndex: which block number this is
 * - startIndex: starting row (for A) or starting column (for B)
 * - endIndex: ending row (for A) or ending column (for B)
 * - totalRows, totalCols: dimensions of the full matrix
 * - blockSize: number of rows (A) or columns (B) per block
 */
public class MatrixBlock implements Serializable {
    private static final long serialVersionUID = 1L;

    private String matrixId; // "A" or "B"
    private int blockIndex;
    private int startIndex; // startRow for A, startCol for B
    private int endIndex;
    private int totalRows;
    private int totalCols;
    private int blockSize;
    private int[][] data;

    public MatrixBlock() {
    }

    public MatrixBlock(String matrixId, int blockIndex, int startIndex, int endIndex,
            int totalRows, int totalCols, int blockSize, int[][] data) {
        this.matrixId = matrixId;
        this.blockIndex = blockIndex;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.totalRows = totalRows;
        this.totalCols = totalCols;
        this.blockSize = blockSize;
        this.data = data;
    }

    // Getters and setters
    public String getMatrixId() {
        return matrixId;
    }

    public void setMatrixId(String matrixId) {
        this.matrixId = matrixId;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getTotalCols() {
        return totalCols;
    }

    public void setTotalCols(int totalCols) {
        this.totalCols = totalCols;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int[][] getData() {
        return data;
    }

    public void setData(int[][] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "MatrixBlock{" + matrixId + ", block=" + blockIndex +
                ", start=" + startIndex + ", end=" + endIndex +
                ", size=" + blockSize + "}";
    }
}
