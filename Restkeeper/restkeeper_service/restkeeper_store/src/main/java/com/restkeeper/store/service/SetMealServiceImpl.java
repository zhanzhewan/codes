package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.SetMeal;
import com.restkeeper.store.entity.SetMealDish;
import com.restkeeper.store.mapper.SetMealMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service("setMealService")
@Service(version = "1.0.0",protocol = "dubbo")
public class SetMealServiceImpl extends ServiceImpl<SetMealMapper, SetMeal> implements ISetMealService {

    @Autowired
    @Qualifier("setMealDishService")
    private ISetMealDishService setMealDishService;

    @Override
    public IPage<SetMeal> queryPage(int pageNum, int pageSize, String name) {

        IPage<SetMeal> page = new Page<>(pageNum,pageSize);

        QueryWrapper<SetMeal> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(name)){
            queryWrapper.lambda().like(SetMeal::getName,name);
        }
        return this.page(page,queryWrapper);
    }

    @Override
    @Transactional
    public boolean add(SetMeal setMeal, List<SetMealDish> setMealDishes) {

        try {
            //保存套餐基础信息
            this.save(setMeal);


            //保存套餐与菜品的关联信息
            setMealDishes.forEach((setMealDish)->{
                setMealDish.setSetMealId(setMeal.getId());
                setMealDish.setIndex(0);
            });
            setMealDishService.saveBatch(setMealDishes);

            return true;
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }



    }

    @Override
    @Transactional
    public boolean update(SetMeal setMeal, List<SetMealDish> setMealDishes) {

        try {
            //修改套餐基础信息
            this.updateById(setMeal);

            //删除原有的菜品关联关系
            if (setMealDishes != null || setMealDishes.size()>0){

                QueryWrapper<SetMealDish> queryWrapper =new QueryWrapper<>();
                queryWrapper.lambda().eq(SetMealDish::getSetMealId,setMeal.getId());
                setMealDishService.remove(queryWrapper);

                //重建菜品的关联关系
                setMealDishes.forEach((setMealDish)->{
                    setMealDish.setSetMealId(setMeal.getId());
                });

                setMealDishService.saveBatch(setMealDishes);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    @Transactional
    public boolean forbiddenSetMeals(List<String> ids) {

        //校验参数
        if (ids == null || ids.isEmpty()){
            throw new BussinessException("没有需要修改的套餐信息");
        }

        //批量修改套餐的状态为停用
        UpdateWrapper<SetMeal> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(SetMeal::getStatus, SystemCode.FORBIDDEN).in(SetMeal::getId,ids);
        return this.update(updateWrapper);
    }

    @Autowired
    @Qualifier("dishService")
    private IDishService dishService;

    @Override
    @Transactional
    public boolean enableSetMeals(List<String> ids) {

        try {
            //校验参数
            if (ids == null || ids.isEmpty()){
                throw new BussinessException("没有需要修改的套餐信息");
            }


            //获取套餐下的所有菜品信息
            QueryWrapper<SetMealDish> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("DISTINCT dish_id").lambda().in(SetMealDish::getSetMealId,ids);
            List<SetMealDish> setMealDishList = setMealDishService.list(queryWrapper);

            //判断菜品的状态
            List<String> dishList = setMealDishList.stream().map(d -> d.getDishId()).collect(Collectors.toList());
            if (dishList != null && !dishList.isEmpty()){
                //套餐下有菜品信息的
                //判断菜品是否处于停售的状态
                QueryWrapper<Dish> queryDishWrapper = new QueryWrapper<>();
                queryDishWrapper.lambda().eq(Dish::getStatus,SystemCode.FORBIDDEN).in(Dish::getId,dishList);
                List<Dish> dishes = dishService.list(queryDishWrapper);
                if (!dishes.isEmpty()){
                    throw new BussinessException("套餐下有停售的菜品，不能起售该套餐");
                }

            }

            //如果套餐下没有停售的菜品
            //修改套餐为起售状态
            UpdateWrapper<SetMeal> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().set(SetMeal::getStatus,SystemCode.ENABLED).in(SetMeal::getId,ids);
            this.update(updateWrapper);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
}
