package com.github.wangjin.simpletaskscheduler.listener;

/**
 * @author Jin Wang
 * @version 1.0
 * @date 2019-11-07 5:55 下午
 */

import com.alibaba.fastjson.JSON;
import com.github.wangjin.simpletaskscheduler.annotation.TaskHandler;
import com.github.wangjin.simpletaskscheduler.entity.TaskScheduler;
import com.github.wangjin.simpletaskscheduler.handler.ITaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.wangjin.simpletaskscheduler.constant.Constants.QICHACHA_ENTERPRISE_CRAWLER_CHANNEL;
import static com.github.wangjin.simpletaskscheduler.constant.Constants.QICHACHA_ENTERPRISE_CRAWLER_TASK;
import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
public class TaskSchedulerListener implements MessageListener {

    private ApplicationContext applicationContext;

    private StringRedisTemplate stringRedisTemplate;

    private String executorName;

    private static final String TASK_PRE = "TASK-";

    public TaskSchedulerListener(ApplicationContext applicationContext, StringRedisTemplate stringRedisTemplate, String executorName) {
        this.applicationContext = applicationContext;
        this.stringRedisTemplate = stringRedisTemplate;
        this.executorName = executorName;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!isEmpty(message.getBody())) {
            //构建taskScheduler对象
            TaskScheduler taskScheduler = JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8), TaskScheduler.class);
            // 是否单节点任务，为true使用任务ID作为redis锁执行任务，其他跳过
            boolean singleNode = taskScheduler.getIsSingleNode() == 1;
            // 配置执行器名称后，如果名称不一致，则不执行后续
            if (!isEmpty(this.executorName) && !isEmpty(taskScheduler.getExecutorName()) && !taskScheduler.getExecutorName().equals(this.executorName)) {
                log.warn("执行器名称未配置或不一致，配置执行器名称：{},当前执行器名称：{}", this.executorName, executorName);
                return;
            }

            // 获取注解为TaskHandler的bean
            Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(TaskHandler.class);
            if (!beansWithAnnotation.isEmpty()) {
                if (singleNode) {
                    String lockName = TASK_PRE + taskScheduler.getId() + ":" + taskScheduler.getRandomId();
                    Long increment = stringRedisTemplate.opsForValue().increment(lockName);
                    stringRedisTemplate.expire(lockName, 5, TimeUnit.SECONDS);
                    if (increment == null || increment != 1) {
                        // 未获得锁则跳过后续执行
                        try {
                            log.warn("当前节点[{}]未竞争到单节点锁，结束调度", InetAddress.getLocalHost().getHostName());
                        } catch (UnknownHostException e) {
                            log.error("获取hostname失败", e);
                        }
                        return;
                    }
                }
                ITaskHandler iTaskHandler = (ITaskHandler) beansWithAnnotation.get(taskScheduler.getHandlerName());
                if (iTaskHandler != null) {
                    try {
                        String execute = iTaskHandler.execute(taskScheduler.getParams());
                        if (QICHACHA_ENTERPRISE_CRAWLER_TASK.equals(taskScheduler.getHandlerName())) {
                            stringRedisTemplate.convertAndSend(QICHACHA_ENTERPRISE_CRAWLER_CHANNEL, JSON.toJSONString(taskScheduler));
                        }
                    } catch (Exception e) {
                        log.error("[Simple-Task-Scheduler] interrupted by exception", e);
                    }
                }
            }
        }
    }

}
