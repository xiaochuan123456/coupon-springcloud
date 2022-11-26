package com.geekbang.coupon.customer.event;

import com.geekbang.coupon.customer.api.beans.RequestCoupon;
import com.geekbang.coupon.customer.service.intf.CouponCustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CouponConsumer {

    @Autowired
    private CouponCustomerService customerService;

    @Bean
    public Consumer<RequestCoupon> addCoupon() {
        return request -> {
            log.info("received: {}", request);
            customerService.requestCoupon(request);
        };
    }

    // 延迟消息
    @Bean
    public Consumer<RequestCoupon> addCouponDelay() {
        return request -> {
            log.info("received: {}", request);
            customerService.requestCoupon(request);
        };
    }

    @Bean
    public Consumer<String> deleteCoupon() {
        return request -> {
            log.info("received: {}", request);
            List<Long> params = Arrays.stream(request.split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
            customerService.deleteCoupon(params.get(0), params.get(1));
        };
    }

    // 消费失败后触发一段降级流程
    // 如果设置了多次本地重试，那么只有最后一次重试失败才会执行这段降级流程
    @ServiceActivator(inputChannel = "request-coupon-topic.add-coupon-group.errors")
    public void requestCouponFallback(ErrorMessage errorMessage) throws Exception {
        log.info("consumer error: {}", errorMessage);

        throw new RuntimeException("打到死信队列");
    }
}
