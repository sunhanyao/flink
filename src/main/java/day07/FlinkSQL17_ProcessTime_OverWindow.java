package day07;

import bean.SensorReading;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Over;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.types.Row;

/*
 *
 *@Author:shy
 *@Date:2020/12/18 20:35
 *
 */
public class FlinkSQL17_ProcessTime_OverWindow {
    public static void main(String[] args) throws Exception {
        //1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        //获取TableAPI执行环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        //2.读取端口数据转换为JavaBean
        SingleOutputStreamOperator<SensorReading> sensorDS = env.socketTextStream("hadoop102", 9999)
                .map(line -> {
                    String[] fields = line.split(",");
                    return new SensorReading(fields[0],
                            Long.parseLong(fields[1]),
                            Double.parseDouble(fields[2]));
                });

        //3.将流转换为表并指定处理时间字段
        Table table = tableEnv.fromDataStream(sensorDS, "id,ts,temp,pt.proctime");

        //4.基于时间的滚动窗口TableAPI
        Table tableResult = table.window(Over.partitionBy("id").orderBy("pt").as("ow"))
                .select("id,id.count over ow,temp.max over ow");

        //基于时间得滚动窗口SQLAPI
        tableEnv.createTemporaryView("sensor",table);
        Table sqlResult = tableEnv.sqlQuery("select id ,count(id) over(partition by id order by pt) ct " +
                "from sensor");
        //6.转换为流进行输出
        tableEnv.toRetractStream(tableResult, Row.class).print("Table");
        tableEnv.toRetractStream(sqlResult, Row.class).print("SQL");

        //7.执行任务
        env.execute();

    }
}
