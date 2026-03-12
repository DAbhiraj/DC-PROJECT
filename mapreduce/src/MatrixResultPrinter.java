import java.io.*;
import java.util.*;

/**
 * Reads MapReduce reducer output from stdin and assembles it into a full
 * matrix.
 * 
 * Input format (one line per entry):
 * 0,0\tRow 0, Cols 0-1: 14,6
 * 0,0\tRow 1, Cols 0-1: 8,11
 * ...
 */
public class MatrixResultPrinter {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: MatrixResultPrinter <M> <P>");
            System.exit(1);
        }
        int M = Integer.parseInt(args[0]);
        int P = Integer.parseInt(args[1]);

        int[][] C = new int[M][P];

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            // Format: "0,0\tRow 2, Cols 0-1: 14,6"
            String[] parts = line.split("\t", 2);
            if (parts.length < 2)
                continue;
            String data = parts[1]; // "Row 2, Cols 0-1: 14,6"

            // Parse row index
            int rowIdx = Integer.parseInt(data.replaceAll("Row (\\d+),.*", "$1"));
            // Parse column start
            int colStart = Integer.parseInt(data.replaceAll(".*Cols (\\d+)-.*", "$1"));
            // Parse values
            String valsPart = data.replaceAll(".*: ", "");
            String[] vals = valsPart.split(",");
            for (int j = 0; j < vals.length; j++) {
                C[rowIdx][colStart + j] = Integer.parseInt(vals[j].trim());
            }
        }

        System.out.println("Matrix C = A x B (MapReduce distributed result):");
        for (int[] row : C) {
            StringBuilder sb = new StringBuilder("  ");
            for (int j = 0; j < row.length; j++) {
                if (j > 0)
                    sb.append("  ");
                sb.append(String.format("%4d", row[j]));
            }
            System.out.println(sb.toString());
        }
    }
}
