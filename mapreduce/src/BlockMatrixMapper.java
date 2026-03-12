import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for Block Matrix Multiplication.
 * 
 * Input: Each file is a block file (A_block_i.txt or B_block_j.txt).
 * First line is metadata, remaining lines are data.
 * 
 * Strategy:
 * For A block i (rows [i*b .. (i+1)*b-1], all n columns):
 * Emit key = "i,j" for every B-block index j, value = "A,<block_data>"
 * 
 * For B block j (columns [j*b .. (j+1)*b-1], all n rows):
 * Emit key = "i,j" for every A-block index i, value = "B,<block_data>"
 * 
 * The reducer receives all A-block-i and B-block-j pairs for key "i,j"
 * and computes the sub-block C[i,j].
 */
public class BlockMatrixMapper extends Mapper<LongWritable, Text, Text, Text> {

    private boolean headerParsed = false;
    private String matrixId;
    private int blockIndex;
    private int startIndex;
    private int totalRows, totalCols, blockSize;
    private List<String> dataLines = new ArrayList<>();

    // How many blocks the other matrix has
    private int numABlocks; // m / b
    private int numBBlocks; // p / b

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        Configuration conf = context.getConfiguration();
        numABlocks = conf.getInt("matrix.a.blocks", 0);
        numBBlocks = conf.getInt("matrix.b.blocks", 0);
    }

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
        String line = value.toString().trim();
        if (line.isEmpty())
            return;

        if (!headerParsed) {
            // First line is metadata
            String[] meta = line.split(",");
            matrixId = meta[0].trim();
            blockIndex = Integer.parseInt(meta[1].trim());
            startIndex = Integer.parseInt(meta[2].trim());
            // meta[3] = endIndex (not needed separately)
            totalRows = Integer.parseInt(meta[4].trim());
            totalCols = Integer.parseInt(meta[5].trim());
            blockSize = Integer.parseInt(meta[6].trim());
            headerParsed = true;
            return;
        }

        dataLines.add(line);
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        // Serialize all data lines into a single string
        StringBuilder dataSB = new StringBuilder();
        dataSB.append(matrixId).append(";");
        dataSB.append(blockIndex).append(";");
        dataSB.append(startIndex).append(";");
        dataSB.append(totalRows).append(";");
        dataSB.append(totalCols).append(";");
        dataSB.append(blockSize).append(";");
        for (int i = 0; i < dataLines.size(); i++) {
            if (i > 0)
                dataSB.append("|");
            dataSB.append(dataLines.get(i));
        }

        String serialized = dataSB.toString();

        // Log which container/task is processing which block
        String taskId = context.getTaskAttemptID().toString();
        System.err.println("====================================================");
        System.err.println("[MAPPER] Container task: " + taskId);
        System.err.println("[MAPPER] Processing block: " + matrixId + "_block_" + blockIndex);

        if ("A".equals(matrixId)) {
            // A block i must be paired with every B block j
            System.err.println("[MAPPER] Emitting A_block_" + blockIndex + " to " + numBBlocks + " reducers: keys 0.."
                    + (numBBlocks - 1));
            for (int j = 0; j < numBBlocks; j++) {
                context.write(new Text(blockIndex + "," + j), new Text(serialized));
            }
        } else if ("B".equals(matrixId)) {
            // B block j must be paired with every A block i
            System.err.println("[MAPPER] Emitting B_block_" + blockIndex + " to " + numABlocks + " reducers: keys 0.."
                    + (numABlocks - 1));
            for (int i = 0; i < numABlocks; i++) {
                context.write(new Text(i + "," + blockIndex), new Text(serialized));
            }
        }
        System.err.println("====================================================");
    }
}
