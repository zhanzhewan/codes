package com.restkeeper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.restkeeper.entity.OrderDetailAllEntity;
import com.restkeeper.entity.ReportDish;
import com.restkeeper.mapper.OrderDetailAllMapper;
import com.restkeeper.mapper.ReportDishMapper;
import com.restkeeper.service.ReportDishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportDishServiceImpl implements ReportDishService {

    @Autowired
    private OrderDetailAllMapper orderDetailAllMapper;

    @Autowired
    private ReportDishMapper reportDishMapper;

    @Override
    @Transactional
    public void generateData() {

        LocalDate end = LocalDate.now();
        LocalDate start = end.plusDays(-1);

        QueryWrapper<OrderDetailAllEntity> wrapper = new QueryWrapper<>();
        wrapper.select("dish_name","dish_category_name","SUM(dish_number) as dish_number","SUM(dish_amount) as dish_price","store_id","shop_id")
                .lambda().ge(OrderDetailAllEntity::getLastUpdateTime,start).lt(OrderDetailAllEntity::getLastUpdateTime,end)
                .eq(OrderDetailAllEntity::getDetailStatus,1)
                .or()
                .eq(OrderDetailAllEntity::getDetailStatus,4)
                .groupBy(OrderDetailAllEntity::getDishName,OrderDetailAllEntity::getDishCategoryName,OrderDetailAllEntity::getShopId,OrderDetailAllEntity::getStoreId);
        List<OrderDetailAllEntity> detailAllEntityList = orderDetailAllMapper.selectList(wrapper);

        detailAllEntityList.forEach(d->{
            ReportDish reportDish = new ReportDish();
            reportDish.setCategory(d.getDishCategoryName());
            reportDish.setDishMoney(d.getDishPrice());
            reportDish.setDishName(d.getDishName());
            reportDish.setDishNumber(d.getDishNumber());
            reportDish.setPayDate(start);
            reportDish.setShopId(d.getShopId());
            reportDish.setStoreId(d.getStoreId());
            reportDish.setIsDeleted(0);
            reportDish.setLastUpdateTime(LocalDateTime.now());
            reportDishMapper.insert(reportDish);
        });
    }
}
