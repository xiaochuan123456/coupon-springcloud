package com.geekbang.coupon.calculation.api.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    // 你可以试着搭建一个商品中心，用来存储商品信息，逐步完善这个应用
    private Long productId;

    // 商品的价格
    private long price;

    // 商品在购物车里的数量
    private Integer count;

    // 商品销售的门店
    //这个计算优惠卷是以门店维度计算，不能在同一个订单中有多个门店的商品。当前只是一个门店的维度
    private Long shopId;

}
