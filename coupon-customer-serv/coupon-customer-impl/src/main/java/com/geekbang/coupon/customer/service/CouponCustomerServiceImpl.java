package com.geekbang.coupon.customer.service;


import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.customer.api.beans.RequestCoupon;
import com.geekbang.coupon.customer.api.beans.SearchCoupon;
import com.geekbang.coupon.customer.api.enums.CouponStatus;
import com.geekbang.coupon.customer.dao.CouponDao;
import com.geekbang.coupon.customer.dao.entity.Coupon;
import com.geekbang.coupon.customer.feign.CalculationService;
import com.geekbang.coupon.customer.feign.TemplateService;
import com.geekbang.coupon.customer.service.intf.CouponCustomerService;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.transaction.Transactional;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.geekbang.coupon.customer.constant.Constant.TRAFFIC_VERSION;

@Slf4j
@Service
public class CouponCustomerServiceImpl implements CouponCustomerService {

    @Autowired
    private CouponDao couponDao;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private CalculationService calculationService;


    @Override
    public SimulationResponse simulateOrderPrice(SimulationOrder order) {
        List<CouponInfo> couponInfos = Lists.newArrayList();
        // ?????????????????????????????????????????????
        // ??????????????????????????????????????????????????????????????????????????????
        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????template??????
        for (Long couponId : order.getCouponIDs()) {
            Coupon example = Coupon.builder()
                    .userId(order.getUserId())
                    .id(couponId)
                    .status(CouponStatus.AVAILABLE)
                    .build();
            Optional<Coupon> couponOptional = couponDao.findAll(Example.of(example))
                    .stream()
                    .findFirst();
            // ???????????????????????????
            if (couponOptional.isPresent()) {
                Coupon coupon = couponOptional.get();
                CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
                //springboot??????
                //couponInfo.setTemplate(templateService.loadTemplateInfo(coupon.getTemplateId()));
                //webflux??????
                //couponInfo.setTemplate(loadTemplateInfo(coupon.getTemplateId()));
                //openfeign??????
                CouponTemplateInfo templateInfo = templateService.getTemplate(couponInfo.getTemplateId());
                couponInfos.add(couponInfo);
            }
        }
        order.setCouponInfos(couponInfos);

        // ????????????????????????
        //return calculationService.simulateOrder(order);
        //webflux??????
//        return webClientBuilder.build().post()
//                .uri("http://coupon-calculation-serv/calculator/simulate")
//                .bodyValue(order)
//                .retrieve()
//                .bodyToMono(SimulationResponse.class)
//                .block();


        //openfeign??????
        return calculationService.simulate(order);

    }

    /**
     * ??????????????????????????????
     */
    @Override
    public List<CouponInfo> findCoupon(SearchCoupon request) {
        // ??????????????????????????????????????????????????????????????????????????????????????????????????????
        Coupon example = Coupon.builder()
                .userId(request.getUserId())
                .status(CouponStatus.convert(request.getCouponStatus()))
                .shopId(request.getShopId())
                .build();

        // ???????????????????????????????????????
        List<Coupon> coupons = couponDao.findAll(Example.of(example));
        if (coupons.isEmpty()) {
            return Lists.newArrayList();
        }

        List<Long> templateIds = coupons.stream()
                .map(Coupon::getTemplateId)
                .collect(Collectors.toList());

//        Map<Long, CouponTemplateInfo> templateMap = templateService.getTemplateInfoMap(templateIds);
//        coupons.stream().forEach(e -> e.setTemplateInfo(templateMap.get(e.getTemplateId())));

        // ?????????????????????????????????
        //webflux??????
//        Map<Long, CouponTemplateInfo> templateMap = webClientBuilder.build().get()
//                .uri("http://coupon-template-serv/template/getBatch?ids=" + templateIds)
//                .retrieve()
//                // ?????????????????????
//                .bodyToMono(new ParameterizedTypeReference<Map<Long, CouponTemplateInfo>>() {})
//                .block();

        // openfeign??????
        Map<Long, CouponTemplateInfo> templateMap = templateService.getTemplateInBatch(templateIds);
        coupons.stream().forEach(e -> e.setTemplateInfo(templateMap.get(e.getTemplateId())));

        return coupons.stream()
                .map(CouponConverter::convertToCoupon)
                .collect(Collectors.toList());
    }

    /**
     * ?????????????????????
     */
    @Override
    public Coupon requestCoupon(RequestCoupon request) {
        //CouponTemplateInfo templateInfo = templateService.loadTemplateInfo(request.getCouponTemplateId());

        //webflux??????
//        CouponTemplateInfo templateInfo = webClientBuilder.build()
//                // ?????????????????????GET??????
//                .get()
//                .uri("http://coupon-template-serv/template/getTemplate?id=" + request.getCouponTemplateId())
//                .header(TRAFFIC_VERSION, request.getTrafficVersion())
//                .retrieve()
//                .bodyToMono(CouponTemplateInfo.class)
//                .block();

        //openfeign??????
        CouponTemplateInfo templateInfo = templateService.getTemplate(request.getCouponTemplateId());

        // ????????????????????????
        if (templateInfo == null) {
            log.error("invalid template id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("Invalid template id");
        }

        // ??????????????????
        long now = Calendar.getInstance().getTimeInMillis();
        Long expTime = templateInfo.getRule().getDeadline();
        if (expTime != null && now >= expTime || BooleanUtils.isFalse(templateInfo.getAvailable())) {
            log.error("template is not available id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("template is unavailable");
        }

        // ??????????????????????????????
        Long count = couponDao.countByUserIdAndTemplateId(request.getUserId(), request.getCouponTemplateId());
        if (count >= templateInfo.getRule().getLimitation()) {
            log.error("exceeds maximum number");
            throw new IllegalArgumentException("exceeds maximum number");
        }

        Coupon coupon = Coupon.builder()
                .templateId(request.getCouponTemplateId())
                .userId(request.getUserId())
                .shopId(templateInfo.getShopId())
                .status(CouponStatus.AVAILABLE)
                .build();
        couponDao.save(coupon);
        return coupon;
    }

    @Override
    @Transactional
    public ShoppingCart placeOrder(ShoppingCart order) {
        if (CollectionUtils.isEmpty(order.getProducts())) {
            log.error("invalid check out request, order={}", order);
            throw new IllegalArgumentException("cart if empty");
        }

        Coupon coupon = null;
        if (order.getCouponId() != null) {
            // ??????????????????????????????????????????????????????????????????
            Coupon example = Coupon.builder()
                    .userId(order.getUserId())
                    .id(order.getCouponId())
                    .status(CouponStatus.AVAILABLE)
                    .build();
            coupon = couponDao.findAll(Example.of(example))
                    .stream()
                    .findFirst()
                    // ????????????????????????????????????
                    .orElseThrow(() -> new RuntimeException("Coupon not found"));

            CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
            //couponInfo.setTemplate(templateService.loadTemplateInfo(coupon.getTemplateId()));
            order.setCouponInfos(Lists.newArrayList(couponInfo));
        }

        // order??????
        //springboot??????
        //ShoppingCart checkoutInfo = calculationService.calculateOrderPrice(order);
        //webflux??????
//        ShoppingCart checkoutInfo = webClientBuilder.build().post()
//                .uri("http://coupon-calculation-serv/calculator/checkout")
//                .bodyValue(order)
//                .retrieve()
//                .bodyToMono(ShoppingCart.class)
//                .block();
        //openfeign??????
        ShoppingCart checkoutInfo = calculationService.checkout(order);

        if (coupon != null) {
            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
            if (CollectionUtils.isEmpty(checkoutInfo.getCouponInfos())) {
                log.error("cannot apply coupon to order, couponId={}", coupon.getId());
                throw new IllegalArgumentException("coupon is not applicable to this order");
            }

            log.info("update coupon status to used, couponId={}", coupon.getId());
            coupon.setStatus(CouponStatus.USED);
            couponDao.save(coupon);
        }

        return checkoutInfo;
    }

    private CouponTemplateInfo loadTemplateInfo(Long templateId) {
        return webClientBuilder.build().get()
                .uri("http://coupon-template-serv/template/getTemplate?id=" + templateId)
                .retrieve()
                .bodyToMono(CouponTemplateInfo.class)
                .block();
    }

    // ?????????????????????
    @Override
    public void deleteCoupon(Long userId, Long couponId) {
        Coupon example = Coupon.builder()
                .userId(userId)
                .id(couponId)
                .status(CouponStatus.AVAILABLE)
                .build();
        Coupon coupon = couponDao.findAll(Example.of(example))
                .stream()
                .findFirst()
                // ????????????????????????????????????
                .orElseThrow(() -> new RuntimeException("Could not find available coupon"));

        coupon.setStatus(CouponStatus.INACTIVE);
        couponDao.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCouponTemplate(Long templateId) {
        templateService.deleteTemplate(templateId);
        couponDao.deleteCouponInBatch(templateId, CouponStatus.INACTIVE);

        throw new RuntimeException("AT????????????????????????");
    }

}
