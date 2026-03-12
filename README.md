# Distributed Matrix Multiplication

## Overview

Block-based matrix multiplication using two approaches:

1. **MapReduce** (Hadoop pseudo-distributed mode via Docker)
2. **Socket Programming** (Java TCP sockets)

## Storage Specifications

- **Matrix A** [m×n]: stored in **row-major** format, split into blocks of `b` rows each
- **Matrix B** [n×p]: stored in **column-major** format, split into blocks of `b` columns each
- m, n, p are all divisible by block size `b`

## Block Metadata

Each block file contains a header line with metadata:

```
MATRIX_ID, BLOCK_INDEX, START_ROW/COL, END_ROW/COL, TOTAL_ROWS, TOTAL_COLS, BLOCK_SIZE
```

## Project Structure

```
├── mapreduce/                    # Approach 1: Hadoop MapReduce
│   ├── src/
│   │   ├── MatrixGenerator.java           # Generates matrix blocks
│   │   ├── StandaloneMapReduceSimulator.java  # Local simulator (no Hadoop)
│   │   ├── MatrixBlockWritable.java       # Custom Hadoop Writable
│   │   ├── BlockMatrixMapper.java         # Hadoop Mapper
│   │   ├── BlockMatrixReducer.java        # Hadoop Reducer
│   │   └── BlockMatrixMultiply.java       # Hadoop Driver
│   ├── Dockerfile                         # Uses apache/hadoop:3 image
│   ├── docker-compose.yml                 # Container orchestration
│   ├── entrypoint.sh                      # Starts Hadoop daemons
│   ├── run_standalone.bat                 # Run without Docker
│   └── run.bat                            # Run with Docker
│
├── socket/                       # Approach 2: Socket Programming
│   ├── src/
│   │   ├── MatrixBlock.java
│   │   ├── MatrixUtils.java
│   │   ├── WorkerNode.java
│   │   ├── MasterNode.java
│   │   └── SocketMatrixMultiply.java
│   ├── build.bat
│   └── run.bat
│
└── README.md
```

---

## Approach 1: MapReduce with Hadoop Pseudo-Distributed Mode

### What is Pseudo-Distributed Mode?

**Pseudo-distributed mode** runs all Hadoop daemons (NameNode, DataNode, ResourceManager, NodeManager) on a **single machine** but as **separate JVM processes** simulating a distributed cluster. This differs from:

- **Local mode**: Single JVM, no HDFS, no YARN
- **Fully distributed mode**: Multiple physical/virtual machines

### How Pseudo-Distributed Mode is Ensured

Our setup guarantees pseudo-distributed mode through:

#### 1. **Dockerfile Configuration** (`mapreduce/Dockerfile`)

Uses the official `apache/hadoop:3` Docker image with Hadoop pre-installed and configures:

**Core HDFS Settings** (`core-site.xml`):

```xml
<property>
  <name>fs.defaultFS</name>
  <value>hdfs://localhost:9000</value>  <!-- HDFS namenode address -->
</property>
```

**HDFS Replication** (`hdfs-site.xml`):

```xml
<property>
  <name>dfs.replication</name>
  <value>1</value>  <!-- Single-node replication -->
</property>
<property>
  <name>dfs.namenode.name.dir</name>
  <value>/hadoop/dfs/name</value>  <!-- NameNode metadata -->
</property>
<property>
  <name>dfs.datanode.data.dir</name>
  <value>/hadoop/dfs/data</value>  <!-- DataNode storage -->
</property>
```

**YARN Configuration** (`yarn-site.xml`):

```xml
<property>
  <name>yarn.nodemanager.aux-services</name>
  <value>mapreduce_shuffle</value>  <!-- Enable MapReduce shuffle -->
</property>
```

**MapReduce Framework** (`mapred-site.xml`):

```xml
<property>
  <name>mapreduce.framework.name</name>
  <value>yarn</value>  <!-- Use YARN for resource management -->
</property>
```

#### 2. **Daemon Startup** (`mapreduce/entrypoint.sh`)

Starts all daemons as separate background processes:

```bash
hdfs namenode &       # HDFS NameNode (metadata)
hdfs datanode &       # HDFS DataNode (storage)
yarn resourcemanager &  # YARN ResourceManager (job scheduling)
yarn nodemanager &    # YARN NodeManager (task execution)
```

Each runs as an independent process communicating via IPC/RPC.

#### 3. **Verification of Distributed Operation**

The logs show distributed behavior:

- **HDFS operations**: Files stored in HDFS with replication
- **YARN containers**: Jobs executed in isolated containers
- **MapReduce phases**: Separate Map and Reduce tasks
- **Job tracking**: ResourceManager coordinates execution

### Step-by-Step Instructions to Run MapReduce

#### Prerequisites

- Docker Desktop installed and running
- At least 4GB RAM available for the container

#### Method 1: Using Docker (Recommended)

**Step 1**: Navigate to the mapreduce directory

```bash
cd "c:\Users\dusha\Desktop\coding\Distributed Systems Project\mapreduce"
```

**Step 2**: Start the Hadoop container

```bash
docker-compose up --build
```

**What happens during startup:**

1. **Image build** (~2-3 min first time):
   - Pulls `apache/hadoop:3` base image
   - Installs Java JDK for compilation
   - Copies Hadoop XML configs
   - Copies source files

2. **Container starts**:
   - Formats HDFS NameNode (first run only)
   - Starts NameNode, DataNode, ResourceManager, NodeManager
   - Waits for HDFS to become ready

3. **Matrix generation**:
   - Compiles `MatrixGenerator.java`
   - Generates block files for A (row-major) and B (column-major)
   - Displays input matrices

4. **HDFS upload**:
   - Creates directories `/matrix/input_A` and `/matrix/input_B`
   - Uploads block files to HDFS
   - Lists uploaded files

5. **MapReduce compilation**:
   - Compiles Mapper, Reducer, Driver classes
   - Creates JAR file `matrix-multiply.jar`

6. **Job execution**:
   - Submits job to YARN ResourceManager
   - **MAP phase**: Each mapper reads A/B blocks, emits (i,j) → block_data
   - **SHUFFLE/SORT phase**: Groups by key (i,j)
   - **REDUCE phase**: Each reducer multiplies A_block_i × B_block_j
   - Writes results to `/matrix/output`

7. **Results display**:
   - Shows MapReduce output (result blocks)
   - Runs naive multiplication for verification
   - Compares results

**Expected Output:**

```
=== Step 5: Results ===
MapReduce Output:
0,0     Row 0, Cols 0-1: 84,81
0,0     Row 1, Cols 0-1: 102,111
0,1     Row 0, Cols 2-3: 105,107
0,1     Row 1, Cols 2-3: 108,132
1,0     Row 2, Cols 0-1: 48,88
1,0     Row 3, Cols 0-1: 66,82
1,1     Row 2, Cols 2-3: 84,65
1,1     Row 3, Cols 2-3: 102,82

=== Step 6: Verification (naive multiplication) ===
Matrix C = A x B (expected):
  [Same values showing verification passes]

========================================
 DONE! Matrix multiplication complete.
========================================
```

**Step 3**: Access Hadoop Web UIs (while container is running)

- **HDFS NameNode**: http://localhost:9870
- **YARN ResourceManager**: http://localhost:8088

**Step 4**: Stop the container

```bash
docker-compose down
```

To remove volumes and start fresh:

```bash
docker-compose down -v
```

#### Method 2: Standalone Simulator (No Docker Required)

For testing the MapReduce logic without Hadoop installation:

**Step 1**: Run the simulator

```bash
cd "c:\Users\dusha\Desktop\coding\Distributed Systems Project\mapreduce"
run_standalone.bat
```

**What it does:**

- Simulates MAP, SHUFFLE/SORT, and REDUCE phases locally
- Uses the same block-based multiplication logic
- No HDFS or YARN required
- Good for debugging and understanding the algorithm

**Expected Output:**

```
==========================================================
 Block Matrix Multiplication - MapReduce Simulation
==========================================================
[Shows MAP phase emissions]
[Shows SHUFFLE grouping]
[Shows REDUCE phase computations]
[Shows final result matrix]

*** VERIFICATION PASSED! MapReduce result matches expected output ***
```

### Customizing Matrix Dimensions

Edit `docker-compose.yml` environment variables:

```yaml
environment:
  - M=6 # Matrix A rows
  - N=6 # Shared dimension (A cols = B rows)
  - P=6 # Matrix B columns
  - B=3 # Block size (must divide M, N, P evenly)
```

Then run:

```bash
docker-compose up --build
```

### Troubleshooting

**Issue**: "docker-compose not found"

- **Fix**: Install Docker Desktop (includes docker-compose v2)

**Issue**: Container exits immediately

- **Fix**: Check logs: `docker logs hadoop-matrix-multiply`
- Ensure ports 9000, 9870, 8088 are not in use

**Issue**: YARN job fails

- **Fix**: Increase Docker memory to 4GB+ in Docker Desktop settings

**Issue**: Want to rebuild from scratch

- **Fix**: `docker-compose down -v && docker-compose build --no-cache && docker-compose up`

---

## Approach 2: Socket Programming

### How It Works

- **Master node** coordinates work distribution
- **Worker nodes** (TCP servers) perform block multiplication
- Round-robin task assignment
- No Hadoop dependencies

### Step-by-Step Instructions

**Step 1**: Navigate to socket directory

```bash
cd "c:\Users\dusha\Desktop\coding\Distributed Systems Project\socket"
```

**Step 2**: Run (compiles and executes automatically)

```bash
run.bat
```

**What happens:**

1. Compiles all Java source files
2. Launches 2 worker nodes on ports 5001, 5002
3. Master generates matrix blocks
4. Master distributes (A_i, B_j) pairs to workers
5. Workers compute C[i,j] = A_i × B_j
6. Master collects results and assembles C
7. Verifies against naive multiplication

**Expected Output:**

```
======================================================
 Distributed Block Matrix Multiplication
 Using Socket Programming
======================================================
Starting 2 worker nodes...
[Worker:5001] Starting worker on port 5001
[Worker:5002] Starting worker on port 5002

Matrix A (4x4):
  [displays matrix]
Matrix B (4x4):
  [displays matrix]

[Master -> Worker0] Sending task: A_block_0 x B_block_0
[Worker:5001] Multiplication done in 0ms. Result: 2x2
...

Matrix C (distributed) (4x4):
  [displays result]

*** VERIFICATION PASSED: Distributed result matches naive multiplication! ***
```

### Customizing Parameters

Edit `run.bat` to change dimensions:

```batch
java SocketMatrixMultiply 6 6 6 3 2
REM Arguments: m n p blockSize numWorkers
```

### Running Master and Workers Separately

For true distributed testing across machines:

**On Worker Machine 1:**

```bash
javac -d build src\*.java
cd build
java WorkerNode 5001
```

**On Worker Machine 2:**

```bash
java WorkerNode 5002
```

**On Master Machine:**

```bash
java MasterNode 4 4 4 2 <worker1_ip>:5001 <worker2_ip>:5002
```

---

## Key Differences Between Approaches

| Feature              | MapReduce (Hadoop)                           | Socket Programming               |
| -------------------- | -------------------------------------------- | -------------------------------- |
| **Distribution**     | YARN containers (pseudo-distributed)         | TCP worker processes             |
| **Storage**          | HDFS (distributed filesystem)                | Local files                      |
| **Fault Tolerance**  | Built-in (task retry, speculative execution) | Manual implementation needed     |
| **Scalability**      | Designed for 1000s of nodes                  | Good for 10s of workers          |
| **Setup Complexity** | Higher (Docker/Hadoop)                       | Lower (pure Java)                |
| **Real-world Use**   | Industry standard for big data               | Good for custom distributed apps |

---

## Verification

Both approaches include verification by:

1. Computing the result via distributed block multiplication
2. Computing the expected result via naive O(n³) multiplication
3. Comparing element-by-element

**VERIFICATION PASSED** confirms the distributed implementation is correct
