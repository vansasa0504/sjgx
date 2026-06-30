# P2-02 Data Generator

独立 Java main，用于向开发压测库灌入跨月调用日志、原始数据和目录数据。它不挂入业务 Maven 模块，避免引入运行时依赖。

## 编译

```bash
javac -encoding UTF-8 perf/datagen/LoadDataGenerator.java
```

## 运行示例

将 MySQL JDBC 驱动加入 classpath 后执行：

```bash
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=invoke_log --months=6 --per-month=50000 --service=svc-risk --clean
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=raw_data --count=1000000 --task-id=1 --partner-id=1 --clean
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=catalog --count=10000 --clean
```

`--clean` 仅清理对应压测范围：指定服务的调用日志、指定任务的原始数据、`perf-%` 目录数据。
