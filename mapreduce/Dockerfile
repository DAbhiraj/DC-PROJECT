# ============================================================
# Uses the official Apache Hadoop Docker image (Hadoop pre-installed)
# No manual Hadoop download/installation required!
# ============================================================
FROM apache/hadoop:3

USER root

# Fix CentOS 7 EOL repos (mirrors are dead, use vault.centos.org)
RUN sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*.repo 2>/dev/null || true && \
    sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*.repo 2>/dev/null || true

# Install JDK (image only has JRE - we need javac to compile)
RUN yum install -y java-1.8.0-openjdk-devel && yum clean all

# The apache/hadoop:3 image sets these env vars:
#   HADOOP_HOME=/opt/hadoop
#   JAVA_HOME is auto-detected
# Set JAVA_HOME to JDK so javac is available
ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
ENV PATH=${JAVA_HOME}/bin:${PATH}
ENV HADOOP_CONF_DIR=${HADOOP_HOME}/etc/hadoop

# ---- Hadoop XML configs for pseudo-distributed single-node ----

RUN cat > ${HADOOP_CONF_DIR}/core-site.xml <<'XMLEOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://localhost:9000</value>
  </property>
</configuration>
XMLEOF

RUN cat > ${HADOOP_CONF_DIR}/hdfs-site.xml <<'XMLEOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>dfs.replication</name>
    <value>1</value>
  </property>
  <property>
    <name>dfs.namenode.name.dir</name>
    <value>/hadoop/dfs/name</value>
  </property>
  <property>
    <name>dfs.datanode.data.dir</name>
    <value>/hadoop/dfs/data</value>
  </property>
</configuration>
XMLEOF

RUN cat > ${HADOOP_CONF_DIR}/mapred-site.xml <<'XMLEOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>mapreduce.framework.name</name>
    <value>yarn</value>
  </property>
  <property>
    <name>mapreduce.application.classpath</name>
    <value>/opt/hadoop/share/hadoop/mapreduce/*:/opt/hadoop/share/hadoop/mapreduce/lib/*</value>
  </property>
</configuration>
XMLEOF

RUN cat > ${HADOOP_CONF_DIR}/yarn-site.xml <<'XMLEOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>yarn.nodemanager.aux-services</name>
    <value>mapreduce_shuffle</value>
  </property>
  <property>
    <name>yarn.nodemanager.env-whitelist</name>
    <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_HOME,PATH,LANG,TZ</value>
  </property>
  <property>
    <name>yarn.nodemanager.resource.memory-mb</name>
    <value>4096</value>
  </property>
  <property>
    <name>yarn.scheduler.maximum-allocation-mb</name>
    <value>4096</value>
  </property>
  <property>
    <name>yarn.nodemanager.vmem-check-enabled</name>
    <value>false</value>
  </property>
</configuration>
XMLEOF

# Capacity scheduler config (required for YARN ResourceManager)
RUN cat > ${HADOOP_CONF_DIR}/capacity-scheduler.xml <<'XMLEOF'
<?xml version="1.0"?>
<configuration>
  <property>
    <name>yarn.scheduler.capacity.root.queues</name>
    <value>default</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.default.capacity</name>
    <value>100</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.default.maximum-capacity</name>
    <value>100</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.maximum-applications</name>
    <value>10000</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.default.state</name>
    <value>RUNNING</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.default.acl_submit_applications</name>
    <value>*</value>
  </property>
  <property>
    <name>yarn.scheduler.capacity.root.default.acl_administer_queue</name>
    <value>*</value>
  </property>
</configuration>
XMLEOF

# Allow root to run all Hadoop daemons
RUN echo "export JAVA_HOME=${JAVA_HOME}" >> ${HADOOP_CONF_DIR}/hadoop-env.sh && \
    echo "export HDFS_NAMENODE_USER=root" >> ${HADOOP_CONF_DIR}/hadoop-env.sh && \
    echo "export HDFS_DATANODE_USER=root" >> ${HADOOP_CONF_DIR}/hadoop-env.sh && \
    echo "export HDFS_SECONDARYNAMENODE_USER=root" >> ${HADOOP_CONF_DIR}/hadoop-env.sh && \
    echo "export YARN_RESOURCEMANAGER_USER=root" >> ${HADOOP_CONF_DIR}/hadoop-env.sh && \
    echo "export YARN_NODEMANAGER_USER=root" >> ${HADOOP_CONF_DIR}/hadoop-env.sh

# Create work directory
WORKDIR /app

# Copy source files
COPY src/ /app/src/
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
