package com.restkeeper.controller.store;

import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.store.entity.Dish;
import com.restkeeper.store.entity.DishFlavor;
import com.restkeeper.store.service.IDishService;
import com.restkeeper.vo.store.DishFlavorVO;
import com.restkeeper.vo.store.DishVO;
import com.restkeeper.vo.store.SaleStatusVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = { "菜品管理" })
@RestController
@RequestMapping("/dish")
public class DishController {

    @Reference(version = "1.0.0",check = false)
    private IDishService dishService;

    @ApiOperation("添加菜品")
    @PostMapping("/add")
    public boolean add(@RequestBody DishVO dishVO){

        //设置菜品
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishVO,dish);

        //设置口味
        List<DishFlavor> dishFlavorList = setDishFlavors(dishVO);


        return dishService.save(dish,dishFlavorList);
    }

    @ApiOperation("修改菜品")
    @PutMapping("/update")
    public boolean update(@RequestBody DishVO dishVO){

        //设置菜品信息
        Dish dish = dishService.getById(dishVO.getId());
        BeanUtils.copyProperties(dishVO,dish);

        //设置口味信息
        List<DishFlavor> dishFlavorList = setDishFlavors(dishVO);


        return dishService.update(dish,dishFlavorList);
    }

    private List<DishFlavor> setDishFlavors(DishVO dishVO) {
        //设置口味信息
        List<DishFlavorVO> dishFlavorsVO = dishVO.getDishFlavors();
        List<DishFlavor> dishFlavorList = new ArrayList<>();

        for (DishFlavorVO dishFlavorVO : dishFlavorsVO) {

            DishFlavor dishFlavor = new DishFlavor();

            dishFlavor.setFlavorName(dishFlavorVO.getFlavor());
            dishFlavor.setFlavorValue(dishFlavorVO.getFlavorData().toString());

            dishFlavorList.add(dishFlavor);
        }
        return dishFlavorList;
    }


    @ApiOperation("根据id查询菜品相关信息")
    @GetMapping("/{id}")
    public DishVO getDish(@PathVariable("id") String id){

        DishVO dishVO = new DishVO();

        //菜品基础信息
        Dish dish = dishService.getById(id);
        if (dish == null){
            throw new BussinessException("菜品不存在");
        }
        BeanUtils.copyProperties(dish,dishVO);


        //口味信息
        List<DishFlavorVO> dishFlavorVOList = new ArrayList<>();

        List<DishFlavor> dishFlavorList = dish.getFlavorList();

        for (DishFlavor dishFlavor : dishFlavorList) {

            DishFlavorVO dishFlavorVO = new DishFlavorVO();

            //设置口味名称
            dishFlavorVO.setFlavor(dishFlavor.getFlavorName());

            //设置口味标签集合  [加酸, 加甜]
            String flavorValue = dishFlavor.getFlavorValue();

            //加酸, 加甜
            String subFlavorValue = flavorValue.substring(flavorValue.indexOf("[")+1,flavorValue.indexOf("]"));
            if (StringUtils.isNotEmpty(subFlavorValue)){

                String[] flavorArray = subFlavorValue.split(",");
                dishFlavorVO.setFlavorData(Arrays.asList(flavorArray));
            }

            dishFlavorVOList.add(dishFlavorVO);

        }

        dishVO.setDishFlavors(dishFlavorVOList);


        return dishVO;

    }

    @ApiOperation(value = "查询可用的菜品列表")
    @GetMapping("/findEnableDishList/{categoryId}")
    public List<Map<String,Object>> findEnableDishList(@PathVariable String categoryId,
                                                       @RequestParam(value="name",required=false) String name){
        return dishService.findEnableDishListInfo(categoryId, name);
    }

    @ApiOperation("设置菜品售卖状态")
    @PutMapping("/updateStatus")
    public boolean updateStatus(@RequestBody SaleStatusVO saleStatusVO){

        if (SystemCode.FORBIDDEN == saleStatusVO.getStatus()){
            //停售
            return dishService.forbiddenDishes(saleStatusVO.getIds());
        }

        if (SystemCode.ENABLED == saleStatusVO.getStatus()){
            //起售
            return dishService.enableDishes(saleStatusVO.getIds());
        }

        return false;
    }

    /**
     * 删除菜品
     * @param ids
     * @return
     */
    @ApiOperation(value = "删除")
    @DeleteMapping("/delete")
    public boolean delete(@RequestBody List<String> ids){
        return dishService.deleteDishes(ids);
    }
}
