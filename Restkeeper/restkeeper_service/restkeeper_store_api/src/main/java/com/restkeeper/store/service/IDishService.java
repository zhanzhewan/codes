package com.restkeeper.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.DishFlavor;

import java.util.List;
import java.util.Map;

public interface IDishService extends IService<Dish> {

    //新增菜品
    boolean save(Dish dish, List<DishFlavor> dishFlavorList);

    //修改菜品
    boolean update(Dish dish, List<DishFlavor> dishFlavorList);

    //根据分类信息与菜品名称查询相关数据列表
    List<Map<String,Object>> findEnableDishListInfo(String categoryId,String name);

    //停售菜品
    boolean forbiddenDishes(List<String> ids);

    //起售菜品
    boolean enableDishes(List<String> ids);

    //删除菜品
    boolean deleteDishes(List<String> ids);

}
