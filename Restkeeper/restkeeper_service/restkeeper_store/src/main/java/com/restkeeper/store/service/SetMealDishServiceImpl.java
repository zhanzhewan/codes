package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.SetMealDish;
import com.restkeeper.store.mapper.SetMealDishMapper;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

@org.springframework.stereotype.Service("setMealDishService")
@Service(version = "1.0.0",protocol = "dubbo")
public class SetMealDishServiceImpl extends ServiceImpl<SetMealDishMapper, SetMealDish> implements ISetMealDishService {

    @Autowired
    @Qualifier("dishService")
    private IDishService dishService;


    @Override
    public List<Dish> getAllDishBySetMealId(String setMealId) {

        List<String> dishIds = Lists.newArrayList();


        List<SetMealDish> setMealDishList = this.getBaseMapper().selectDishes(setMealId);

        setMealDishList.forEach(setMealDish->{
            dishIds.add(setMealDish.getDishId());
        });

        return dishService.listByIds(dishIds);
    }

    @Override
    public Integer getDishCopiesInSetMeal(String dishId, String setMealId) {

        QueryWrapper<SetMealDish> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(SetMealDish::getDishId,dishId).eq(SetMealDish::getSetMealId,setMealId);
        SetMealDish setMealDish = this.getOne(wrapper);

        return setMealDish == null ?0:setMealDish.getDishCopies();
    }
}
