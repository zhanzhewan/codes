package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.DishCategory;
import com.restkeeper.store.entity.SetMeal;
import com.restkeeper.store.mapper.DishCategoryMapper;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service("dishCategoryService")
@Service(version = "1.0.0",protocol = "dubbo")
public class DishCategoryServiceImpl extends ServiceImpl<DishCategoryMapper, DishCategory> implements IDishCategoryService {

    @Override
    @Transactional
    public boolean add(String name, int type) {

        //判断当前的分类名称在表中是否存在
        this.checkNameExist(name);


        DishCategory dishCategory = new DishCategory();
        dishCategory.setName(name);
        dishCategory.setType(type);
        dishCategory.setTorder(0);

        return this.save(dishCategory);
    }

    @Override
    @Transactional
    public boolean update(String id, String name) {

        //判断当前的分类名称在表中是否存在
        this.checkNameExist(name);

        UpdateWrapper<DishCategory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(DishCategory::getName,name).eq(DishCategory::getCategoryId,id);
        return this.update(updateWrapper);
    }

    @Override
    public IPage<DishCategory> queryPage(int pageNum, int pageSize) {

        IPage<DishCategory> page = new Page<>(pageNum,pageSize);

        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().orderByDesc(DishCategory::getLastUpdateTime);

        return this.page(page,queryWrapper);
    }

    @Override
    public List<Map<String, Object>> findCategoryList(Integer type) {

        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();

        if (type != null){
            queryWrapper.lambda().eq(DishCategory::getType,type);
        }

        queryWrapper.lambda().select(DishCategory::getCategoryId,DishCategory::getName);

        return this.listMaps(queryWrapper);
    }

    @Autowired
    @Qualifier("dishService")
    private IDishService dishService;

    @Autowired
    @Qualifier("setMealService")
    private ISetMealService setMealService;

    @Override
    @Transactional
    public boolean delete(String id) {

        try {
            //根据id查询分类对象
            DishCategory dishCategory = this.getById(id);
            if (dishCategory == null){
                throw new BussinessException("分类不存在");
            }

            //菜品
            if (dishCategory.getType() == 1){
                QueryWrapper<Dish> queryWrapper = new QueryWrapper<>();
                queryWrapper.lambda().eq(Dish::getCategoryId,id);
                int count = dishService.count(queryWrapper);
                if (count > 0){
                    throw new BussinessException("当前分类下已经关联的菜品信息，不允许删除");
                }
            }

            //套餐
            if (dishCategory.getType() == 2){
                QueryWrapper<SetMeal> queryWrapper = new QueryWrapper<>();
                queryWrapper.lambda().eq(SetMeal::getCategoryId,id);
                int count = setMealService.count(queryWrapper);
                if (count > 0){
                    throw new BussinessException("当前分类下已经关联的套餐信息，不允许删除");
                }
            }

            //逻辑删除分类信息
            this.removeById(id);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    private void checkNameExist(String name) {

        QueryWrapper<DishCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().select(DishCategory::getCategoryId).eq(DishCategory::getName,name);

        Integer count = this.getBaseMapper().selectCount(queryWrapper);

        if (count > 0) throw new BussinessException("该分类已经存在");
    }
}
