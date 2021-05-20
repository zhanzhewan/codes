package com.restkeeper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.restkeeper.entity.*;
import com.restkeeper.mapper.*;
import com.restkeeper.service.OrderHistoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class OrderHistoryServiceImpl implements OrderHistoryService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderHistoryMapper orderHistoryMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private OrderDetailHistoryMapper orderDetailHistoryMapper;

    @Autowired
    private OrderDetailMealMapper orderDetailMealMapper;

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public void exportToHistory() {

        LocalDate nowDate = LocalDate.now();

        //查询出小于今天的订单记录
        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
        wrapper.lambda().lt(OrderEntity::getLastUpdateTime,nowDate);
        List<OrderEntity> orderEntityList = orderMapper.selectList(wrapper);

        //遍历集合，嵌入到历史订单表中，同时删除原有的订单记录信息
        orderEntityList.forEach(orderEntity -> {
            //订单主表
            OrderHistoryEntity orderHistoryEntity = new OrderHistoryEntity();
            BeanUtils.copyProperties(orderEntity,orderHistoryEntity);
            orderHistoryMapper.insert(orderHistoryEntity);

            //查询出关联的订单明细(t_order_detail   t_order_detail_meal)
            QueryWrapper<OrderDetailEntity> detailWrapper = new QueryWrapper<>();
            detailWrapper.lambda().eq(OrderDetailEntity::getOrderId,orderEntity.getOrderId());
            List<OrderDetailEntity> orderDetailEntityList = orderDetailMapper.selectList(detailWrapper);

            orderDetailEntityList.forEach(orderDetailEntity -> {
                if (orderDetailEntity.getDishType() == 1){
                    //普通菜品
                    //添加到历史表中t_his_order_detail
                    OrderDetailHistoryEntity orderDetailHistoryEntity = new OrderDetailHistoryEntity();
                    BeanUtils.copyProperties(orderDetailEntity,orderDetailHistoryEntity);
                    orderDetailHistoryMapper.insert(orderDetailHistoryEntity);
                    orderDetailMapper.deleteById(orderDetailEntity.getDetailId());
                }

                if (orderDetailEntity.getDishType() == 2){
                    //套餐。获取当前套餐下的相关菜品信息
                    QueryWrapper<OrderDetailMealEntity> mealWrapper = new QueryWrapper<>();
                    mealWrapper.lambda().eq(OrderDetailMealEntity::getOrderId,orderDetailEntity.getOrderId());
                    List<OrderDetailMealEntity> detailMealEntityList = orderDetailMealMapper.selectList(mealWrapper);

                    for (OrderDetailMealEntity orderDetailMealEntity : detailMealEntityList) {

                        OrderDetailHistoryEntity orderDetailHistoryEntity = new OrderDetailHistoryEntity();
                        BeanUtils.copyProperties(orderDetailMealEntity,orderDetailHistoryEntity);
                        orderDetailHistoryMapper.insert(orderDetailHistoryEntity);
                        orderDetailMealMapper.deleteById(orderDetailMealEntity.getDetailId());
                    }

                }
            });

            //删除订单的信息
            orderMapper.deleteById(orderEntity.getOrderId());

        });

    }
}
