package com.geekbang.coupon.customer.dao;

import com.geekbang.coupon.customer.api.enums.CouponStatus;
import com.geekbang.coupon.customer.dao.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponDao extends JpaRepository<Coupon, Long> {

    long countByUserIdAndTemplateId(Long userId, Long templateId);

    @Modifying
    @Query("update Coupon c set c.status = :status where c.templateId = :templateId")
    int deleteCouponInBatch(@Param("templateId") Long templateId, @Param("status") CouponStatus status);

}
