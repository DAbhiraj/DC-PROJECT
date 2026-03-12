#!/bin/bash
set -e

echo "========================================"
echo " Block Matrix Multiplication - MapReduce"
echo " Using apache/hadoop:3 Docker Image"
echo " Pseudo-Distributed Hadoop Mode"
echo "========================================"

# Matrix dimensions
M=${M:-4}
N=${N:-4}
P=${P:-4}
B=${B:-2}

echo "Matrix A: ${M}x${N}, Matrix B: ${N}x${P}, Block size: ${B}"
echo "HADOOP_HOME=${HADOOP_HOME}"
echo "JAVA_HOME=${JAVA_HOME}"

# Format HDFS (only if not already formatted)
if [ ! -d /hadoop/dfs/name/current ]; then
    echo ""
    echo "Formatting HDFS namenode..."
    hdfs namenode -format -force -nonInteractive 2>&1 | tail -5
fi

# Start Hadoop daemons individually (no SSH required!)
echo ""
echo "Starting HDFS NameNode..."
hdfs namenode &
sleep 5

echo "Starting HDFS DataNode..."
hdfs datanode &
sleep 5

echo "Starting YARN ResourceManager..."
yarn resourcemanager &
sleep 5

echo "Starting YARN NodeManager..."
yarn nodemanager &
sleep 10

# Wait for HDFS to become ready
echo ""
echo "Waiting for HDFS to become ready..."
for i in $(seq 1 30); do
    if hdfs dfs -ls / > /dev/null 2>&1; then
        echo "HDFS is responding!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "WARNING: HDFS may not be fully ready, continuing anyway..."
    fi
    echo "  Waiting... ($i/30)"
    sleep 2
done

# Wait for HDFS to leave safe mode (critical!)
echo ""
echo "Waiting for HDFS to leave safe mode..."
hdfs dfsadmin -safemode wait
echo "HDFS safe mode is OFF. Ready for write operations."

# Verify services
echo ""
echo "HDFS report:"
hdfs dfsadmin -report 2>/dev/null | head -8 || echo "Warning: Could not get HDFS report"

# Step 1: Compile MatrixGenerator and generate input data
echo ""
echo "=== Step 1: Generating matrix block files ==="
cd /app
mkdir -p build
javac src/MatrixGenerator.java -d build/
cd build
java MatrixGenerator $M $N $P $B
cd /app

# Step 2: Upload input to HDFS
echo ""
echo "=== Step 2: Uploading to HDFS ==="
hdfs dfs -mkdir -p /matrix/input_A
hdfs dfs -mkdir -p /matrix/input_B
hdfs dfs -put -f build/input_A/* /matrix/input_A/
hdfs dfs -put -f build/input_B/* /matrix/input_B/
echo "HDFS input_A contents:"
hdfs dfs -ls /matrix/input_A/
echo "HDFS input_B contents:"
hdfs dfs -ls /matrix/input_B/

# Step 3: Compile MapReduce job
echo ""
echo "=== Step 3: Compiling MapReduce job ==="
HADOOP_CP=$(hadoop classpath)
mkdir -p /app/classes
javac -classpath "$HADOOP_CP" -d /app/classes \
    src/MatrixBlockWritable.java \
    src/BlockMatrixMapper.java \
    src/BlockMatrixReducer.java \
    src/BlockMatrixMultiply.java
# Also compile the result printer utility (no Hadoop classpath needed)
javac src/MatrixResultPrinter.java -d /app/classes/
jar -cvf /app/matrix-multiply.jar -C /app/classes/ .

# Step 4: Run MapReduce job
echo ""
echo "=== Step 4: Running MapReduce job ==="
hdfs dfs -rm -r -f /matrix/output
hadoop jar /app/matrix-multiply.jar BlockMatrixMultiply \
    /matrix/input_A /matrix/input_B /matrix/output \
    $M $N $P $B

# Step 5: Show results
echo ""
echo "=== Step 5: Input Matrices & Expected Result ==="
cd /app/build
java MatrixGenerator $M $N $P $B 2>/dev/null
cd /app

echo ""
echo "=== Step 6: MapReduce Distributed Result (assembled from all containers) ==="
javac src/MatrixResultPrinter.java -d /app/classes/ 2>/dev/null
hdfs dfs -cat /matrix/output/part-r-* | java -cp /app/classes MatrixResultPrinter $M $P

echo ""
echo "=== YARN Job Summary ==="
APP_ID=$(yarn application -list -appStates FINISHED 2>/dev/null | grep -E '^application_' | awk '{print $1}' | tail -1)
if [ -n "$APP_ID" ]; then
    echo "Application ID: $APP_ID"
    yarn application -status $APP_ID 2>/dev/null | grep -E 'State|Final-State|Started|Finished|Diagnostics|Num Attempt' | head -10 || true
fi

echo ""
echo "========================================"
echo " DONE! Matrix multiplication complete."
echo "========================================"

# Keep container alive for inspection
tail -f /dev/null
