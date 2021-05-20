package com.restkeeper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.restkeeper.entity.OrderEntity;
import com.restkeeper.entity.ReportPay;
import com.restkeeper.mapper.OrderMapper;
import com.restkeeper.mapper.ReportPayMapper;
import com.restkeeper.service.ReportPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportPayServiceImpl implements ReportPayService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ReportPayMapper reportPayMapper;

    @Override
    @Transactional
    public void generateData() {

        LocalDate end = LocalDate.now();
        LocalDate start = end.plusDays(-1);

        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
        wrapper.select("SUM( total_amount ) AS total_amount","SUM( free_amount ) AS free_amount","SUM( present_amount ) AS present_amount","SUM( small_amount ) AS small_amount","SUM( pay_amount ) AS pay_amount","SUM( person_numbers ) AS person_numbers","COUNT(pay_type) as pay_count","pay_type","store_id","shop_id")
                .lambda().ge(OrderEntity::getLastUpdateTime,start).lt(OrderEntity::getLastUpdateTime,end)
                .groupBy(OrderEntity::getPayType,OrderEntity::getShopId,OrderEntity::getStoreId);
        List<OrderEntity> orderEntityList = orderMapper.selectList(wrapper);

        orderEntityList.forEach(orderEntity -> {
            ReportPay reportPay = new ReportPay();
            reportPay.setFreeAmount(orderEntity.getFreeAmount());
            reportPay.setPayAmount(orderEntity.getPayAmount());
            reportPay.setSmallAmount(orderEntity.getSmallAmount());
            reportPay.setPresentAmount(orderEntity.getPresentAmount());
            reportPay.setPayDate(end);
            reportPay.setPayType(orderEntity.getPayType());
            reportPay.setPersonNumbers(orderEntity.getPersonNumbers());
            reportPay.setShopId(orderEntity.getShopId());
            reportPay.setStoreId(orderEntity.getStoreId());
            reportPay.setTotalAmount(orderEntity.getTotalAmount());
            reportPay.setPayCount(orderEntity.getPayCount());
            reportPay.setIsDeleted(0);
            reportPay.setLastUpdateTime(LocalDateTime.now());
            reportPayMapper.insert(reportPay);
        });
    }
}
