package com.malls.order.task;

// 订单服务：调 closeExpired
import com.malls.order.service.OraderService;
// 构造器注入与日志
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// 组件注册
import org.springframework.stereotype.Component;
// 定时任务注解（启动类上已开 @EnableScheduling）
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 超时关单定时任务。
 *
 * <p>每 60 秒扫一次"待支付且已过截止时间"的订单，把状态推到 CLOSED 并回补库存。
 *
 * <p><b>为什么现在用轮询，而不是延时队列</b>：单机阶段轮询最省事，
 * 也不需要额外中间件。缺点是订单量大了会空扫全表——Day07 接 RabbitMQ 后
 * 改成"下单时投一条 30 分钟延迟消息，到期消费关单"，就不用轮询了。
 *
 * <p>任务本身不加事务：事务边界在 {@code OraderService.closeExpired} 里，
 * 定时器只负责"按时触发"。这样把调度和业务分开，也方便手动调用来做测试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    // 订单服务
    private final OraderService orderService;

    /**
     * 每 60 秒执行一次。
     *
     * <p>{@code fixedDelay} 而不是 {@code fixedRate}：等上一次跑完再计时，
     * 避免单次执行超过间隔时任务堆积。
     *
     * <p>初始延迟 60 秒：给应用启动、数据初始化留出时间。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void closeExpiredOrders() {
        try {
            int closed = orderService.closeExpired();
            if (closed > 0) {
                log.info("超时关单任务：本次关闭 {} 单", closed);
            }
        } catch (Exception e) {
            // 定时任务抛异常会被调度器吞掉，这里显式记一条，别让问题静默
            log.error("超时关单任务执行失败", e);
        }
    }
}
