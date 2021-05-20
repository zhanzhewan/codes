package com.restkeeper.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.SetMealDish;

import java.util.List;

public interface ISetMealDishService extends IService<SetMealDish> {
    List<Dish> getAllDishBySetMealId(String setMealId);

    Integer getDishCopiesInSetMeal(String dishId, String setMealId);
}
