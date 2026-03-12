import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Driver for Block Matrix Multiplication using MapReduce.
 * 
 * Usage: BlockMatrixMultiply <inputA_dir> <inputB_dir> <output_dir> <m> <n>
 * <p>
 * <blockSize>
 * 
 * Example: hadoop jar matrix.jar BlockMatrixMultiply input_A input_B output 4 4
 * 4 2
 */
public class BlockMatrixMultiply {

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: BlockMatrixMultiply <inputA> <inputB> <output> <m> <n> <p> <blockSize>");
            System.exit(1);
        }

        String inputA = args[0];
        String inputB = args[1];
        String output = args[2];
        int m = Integer.parseInt(args[3]);
        int n = Integer.parseInt(args[4]);
        int p = Integer.parseInt(args[5]);
        int blockSize = Integer.parseInt(args[6]);

        int numABlocks = m / blockSize;
        int numBBlocks = p / blockSize;

        System.out.println("=== Block Matrix Multiplication (MapReduce) ===");
        System.out.println("A: " + m + "x" + n + ", B: " + n + "x" + p + ", Block size: " + blockSize);
        int numCBlocks = numABlocks * numBBlocks;
        System.out.println("A blocks: " + numABlocks + ", B blocks: " + numBBlocks + ", C sub-blocks: " + numCBlocks);
        System.out.println("YARN containers expected: " + (numABlocks + numBBlocks) + " mappers + " + numCBlocks
                + " reducers + 1 AM = " + (numABlocks + numBBlocks + numCBlocks + 1) + " total");

        Configuration conf = new Configuration();
        conf.setInt("matrix.a.blocks", numABlocks);
        conf.setInt("matrix.b.blocks", numBBlocks);
        conf.setInt("matrix.m", m);
        conf.setInt("matrix.n", n);
        conf.setInt("matrix.p", p);
        conf.setInt("matrix.blockSize", blockSize);

        Job job = Job.getInstance(conf, "Block Matrix Multiply");
        job.setJarByClass(BlockMatrixMultiply.class);

        job.setMapperClass(BlockMatrixMapper.class);
        job.setReducerClass(BlockMatrixReducer.class);
        // Each C-block gets its own dedicated reducer running in parallel!
        job.setNumReduceTasks(numABlocks * numBBlocks);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Use NLineInputFormat so each mapper gets one complete block file
        // Set lines per map large enough to read entire file in one mapper
        job.setInputFormatClass(NLineInputFormat.class);
        NLineInputFormat.setNumLinesPerSplit(job, blockSize + 1); // metadata + data lines

        FileInputFormat.addInputPath(job, new Path(inputA));
        FileInputFormat.addInputPath(job, new Path(inputB));
        FileOutputFormat.setOutputPath(job, new Path(output));

        boolean success = job.waitForCompletion(true);
        System.out.println(success ? "Job completed successfully!" : "Job failed!");
        System.exit(success ? 0 : 1);
    }
}
