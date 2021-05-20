package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.DishFlavor;
import com.restkeeper.store.entity.SetMeal;
import com.restkeeper.store.entity.SetMealDish;
import com.restkeeper.store.mapper.DishMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service("dishService")
@Service(version = "1.0.0",protocol = "dubbo")
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements IDishService {

    @Autowired
    @Qualifier("dishFlavorService")
    private IDishFlavorService dishFlavorService;

    @Override
    @Transactional
    public boolean save(Dish dish, List<DishFlavor> dishFlavorList) {

        try {
            //保存菜品
            this.save(dish);

            //保存口味
            dishFlavorList.forEach((dishFlavor)->{
                dishFlavor.setDishId(dish.getId());
            });
            dishFlavorService.saveBatch(dishFlavorList);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    @Transactional
    public boolean update(Dish dish, List<DishFlavor> dishFlavorList) {

        try {
            //修改菜品基础信息
            this.updateById(dish);

            //删除原有的口味列表
            QueryWrapper<DishFlavor> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(DishFlavor::getDishId,dish.getId());
            dishFlavorService.remove(queryWrapper);

            //新增口味列表信息
            dishFlavorList.forEach((dishFlavor)->{
                dishFlavor.setDishId(dish.getId());
            });
            dishFlavorService.saveBatch(dishFlavorList);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    @Override
    public List<Map<String, Object>> findEnableDishListInfo(String categoryId, String name) {

        QueryWrapper<Dish> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().select(Dish::getId,Dish::getName,Dish::getStatus,Dish::getPrice);

        if (StringUtils.isNotEmpty(categoryId)){
            queryWrapper.lambda().eq(Dish::getCategoryId,categoryId);
        }

        if (StringUtils.isNotEmpty(name)){
            queryWrapper.lambda().eq(Dish::getName,name);
        }

        queryWrapper.lambda().eq(Dish::getStatus, SystemCode.ENABLED);

        return this.listMaps(queryWrapper);
    }

    @Autowired
    @Qualifier("setMealDishService")
    private ISetMealDishService setMealDishService;

    @Autowired
    @Qualifier("setMealService")
    private ISetMealService setMealService;

    @Override
    @Transactional
    public boolean forbiddenDishes(List<String> ids) {

        try {
            //校验参数
            if (ids == null || ids.isEmpty()){
                throw new BussinessException("校验参数失败");
            }

            //批量修改菜品为停用状态
            UpdateWrapper<Dish> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().set(Dish::getStatus,SystemCode.FORBIDDEN).in(Dish::getId,ids);
            this.update(updateWrapper);

            //判断是否存在关联的套餐
            QueryWrapper<SetMealDish> queryWrapper= new QueryWrapper<>();
            queryWrapper.select("DISTINCT setmeal_id").lambda().in(SetMealDish::getDishId,ids);
            List<SetMealDish> setMealDishList = setMealDishService.list(queryWrapper);

            List<String> setMealIds = setMealDishList.stream().map(d -> d.getSetMealId()).collect(Collectors.toList());

            if (setMealIds!=null && !setMealIds.isEmpty()){

                //如果有，修改关联的套餐为停用状态
                UpdateWrapper<SetMeal> updateWrapper1 = new UpdateWrapper<>();
                updateWrapper1.lambda().set(SetMeal::getStatus,SystemCode.FORBIDDEN).in(SetMeal::getId,setMealIds);
                setMealService.update(updateWrapper1);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }



    }

    @Override
    @Transactional
    public boolean enableDishes(List<String> ids) {

        //校验参数
        if (ids == null || ids.isEmpty()){
            throw new BussinessException("校验参数失败");
        }

        //批量修改菜品状态为起售
        UpdateWrapper<Dish> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(Dish::getStatus,SystemCode.ENABLED).in(Dish::getId,ids);

        return this.update(updateWrapper);
    }

    @Override
    @Transactional
    public boolean deleteDishes(List<String> ids) {

        try {
            //校验参数
            if (ids == null || ids.isEmpty()){
                throw new BussinessException("校验参数失败");
            }

            //查询关联的套餐信息
            QueryWrapper<SetMealDish> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("DISTINCT setmeal_id").lambda().in(SetMealDish::getDishId,ids);
            List<SetMealDish> setMealDishList = setMealDishService.list(queryWrapper);
            List<String> setMealIds = setMealDishList.stream().map(d -> d.getSetMealId()).collect(Collectors.toList());
            if (!setMealIds.isEmpty()){
                //如果有的话，停售相关的套餐信息
                UpdateWrapper<SetMeal> updateWrapper = new UpdateWrapper<>();
                updateWrapper.lambda().set(SetMeal::getStatus,SystemCode.FORBIDDEN).in(SetMeal::getId,setMealIds);
                setMealService.update(updateWrapper);
            }

            //批量逻辑删除菜品信息
            this.removeByIds(ids);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }


}
