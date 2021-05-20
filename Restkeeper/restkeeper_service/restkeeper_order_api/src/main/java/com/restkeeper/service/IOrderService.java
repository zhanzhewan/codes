package com.restkeeper.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.dto.*;
import com.restkeeper.entity.OrderEntity;

import java.time.LocalDate;
import java.util.List;

public interface IOrderService extends IService<OrderEntity> {

    //下单
    String addOrder(OrderEntity orderEntity);

    //退菜
    boolean returnDish(DetailDTO detailDTO);

    //现金结账
    boolean pay(OrderEntity orderEntity);

    //挂账结账
    boolean pay(OrderEntity orderEntity, CreditDTO creditDTO);

    //换台
    boolean changeTable(String orderId,String targetTableId);

    //获取当日经营数据统计
    CurrentAmountCollectDTO getCurrentAmount(LocalDate start ,LocalDate end);

    //获取当日各时间段的销售数据分析
    //type : 1:销售额金额统计   2：销售总单数统计
    List<CurrentHourCollectDTO> getCurrentHourCollect(LocalDate start ,LocalDate end,Integer type);

    /**
     * 当日店内的收款方式构成
     * @param start
     * @param end
     * @return
     */
    List<PayTypeCollectDTO> getPayTypeCollect(LocalDate start, LocalDate end);

    /**
     * 获取当日优惠数据统计
     * @param start
     * @param end
     * @return
     */
    PrivilegeDTO getPrivilegeCollect(LocalDate start,LocalDate end);

}
