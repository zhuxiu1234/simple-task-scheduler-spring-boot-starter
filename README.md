# Simple Task Scheduler 简单任务调度器

基于Redis sub/pub实现

开启调度配置
```properties
simple.task.scheduler.enabled=true
``` 

Redis配置
```properties
# Redis数据库索引（默认为0）
spring.redis.database=0
# Redis服务器地址
spring.redis.host=localhost
# Redis服务器连接端口
spring.redis.port=6379
# Redis服务器连接密码（默认为空）
spring.redis.password=
# 连接池最大阻塞等待时间（使用负值表示没有限制）
spring.redis.jedis.pool.max-wait=
# 连接池中的最大空闲连接
spring.redis.jedis.pool.max-idle=8
# 连接池中的最小空闲连接
spring.redis.jedis.pool.min-idle=0
# 连接超时时间（毫秒）
spring.redis.timeout=0
```

创建Task
```java
@TaskHandler("demoTask")
public class DemoTask implements ITaskHandler {

    @Override
    public String execute(String params) {
        // your business logic goes here
        return "success";
    }
}
```
