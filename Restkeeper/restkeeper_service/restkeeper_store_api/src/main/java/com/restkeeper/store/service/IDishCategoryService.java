package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.restkeeper.store.entity.DishCategory;

import java.util.List;
import java.util.Map;

public interface IDishCategoryService {

    //新增分类
    boolean add(String name,int type);

    //修改分类
    boolean update(String id,String name);

    //分类分页查询
    IPage<DishCategory> queryPage(int pageNum,int pageSize);

    //根据分类的类别进行列表数据查询
    List<Map<String,Object>> findCategoryList(Integer type);

    //删除分类
    boolean delete(String id);
}
