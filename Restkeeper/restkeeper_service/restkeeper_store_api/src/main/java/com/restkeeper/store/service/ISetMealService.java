package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.store.entity.SetMeal;
import com.restkeeper.store.entity.SetMealDish;

import java.util.List;

public interface ISetMealService extends IService<SetMeal> {

    //分页查询
    IPage<SetMeal> queryPage(int pageNum,int pageSize,String name);

    //新增套餐
    boolean add(SetMeal setMeal, List<SetMealDish> setMealDishes);

    //修改套餐
    boolean update(SetMeal setMeal, List<SetMealDish> setMealDishes);

    //停售套餐
    boolean forbiddenSetMeals(List<String> ids);

    //起售套餐
    boolean enableSetMeals(List<String> ids);

}
