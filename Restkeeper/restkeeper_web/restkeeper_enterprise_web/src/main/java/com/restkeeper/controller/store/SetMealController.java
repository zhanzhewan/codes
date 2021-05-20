package com.restkeeper.controller.store;

import com.google.common.collect.Lists;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.response.vo.PageVO;
import com.restkeeper.store.entity.SetMeal;
import com.restkeeper.store.entity.SetMealDish;
import com.restkeeper.store.service.ISetMealService;
import com.restkeeper.vo.store.SaleStatusVO;
import com.restkeeper.vo.store.SetMealDishVO;
import com.restkeeper.vo.store.SetMealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Api(tags = {"套餐管理"})
@RestController
@RequestMapping("/setMeal")
public class SetMealController {

    @Reference(version = "1.0.0",check = false)
    private ISetMealService setMealService;

    @ApiOperation("套餐分页查询")
    @GetMapping("/queryPage/{page}/{pageSize}")
    public PageVO<SetMeal> queryPage(@PathVariable("page") Integer page,
                                     @PathVariable("pageSize") Integer pageSize,
                                     @RequestParam(value="name",required=false) String name){
        return new PageVO<>(setMealService.queryPage(page, pageSize, name));
    }

    @ApiOperation("添加套餐")
    @PostMapping("/add")
    public boolean add(@RequestBody SetMealVO setMealVO){

        //设置套餐信息
        SetMeal setMeal = new SetMeal();
        BeanUtils.copyProperties(setMealVO,setMeal);
        setMeal.setDishList(null);

        //设置相关的菜品信息
        List<SetMealDish> setMealDishList = Lists.newArrayList();

        if (setMealVO.getDishList() != null){

            setMealVO.getDishList().forEach((d)->{
                SetMealDish setMealDish = new SetMealDish();
                setMealDish.setIndex(0);
                setMealDish.setDishCopies(d.getCopies());
                setMealDish.setDishId(d.getDishId());
                setMealDish.setDishName(d.getDishName());

                setMealDishList.add(setMealDish);
            });
        }

        return setMealService.add(setMeal,setMealDishList);
    }

    @ApiOperation("根据id查询套餐信息")
    @GetMapping("/{id}")
    public SetMealVO getInfo(@PathVariable("id") String id){

        SetMealVO setMealVO = new SetMealVO();

        //套餐基础信息
        SetMeal setMeal = setMealService.getById(id);
        if (setMeal == null){
            throw new BussinessException("套餐不存在");
        }
        BeanUtils.copyProperties(setMeal,setMealVO);


        //相关的菜品集合信息
        List<SetMealDishVO> setMealDishVOList = Lists.newArrayList();

        List<SetMealDish> setMealDishList = setMeal.getDishList();

        for (SetMealDish setMealDish : setMealDishList) {

            SetMealDishVO setMealDishVO = new SetMealDishVO();

            //设置相关信息
            setMealDishVO.setDishId(setMealDish.getDishId());
            setMealDishVO.setDishName(setMealDish.getDishName());
            setMealDishVO.setCopies(setMealDish.getDishCopies());

            setMealDishVOList.add(setMealDishVO);
        }

        setMealVO.setDishList(setMealDishVOList);

        return setMealVO;
    }

    @ApiOperation("修改套餐")
    @PutMapping("/update")
    public boolean update(@RequestBody SetMealVO setMealVO){

        //设置套餐的基础信息
        SetMeal setMeal = setMealService.getById(setMealVO.getId());
        BeanUtils.copyProperties(setMealVO,setMeal);
        setMeal.setDishList(null);


        List<SetMealDish> setMealDishes = Lists.newArrayList();

        if (setMealVO.getDishList() != null){

            setMealVO.getDishList().forEach((d)->{

                SetMealDish setMealDish = new SetMealDish();

                setMealDish.setIndex(0);
                setMealDish.setDishCopies(d.getCopies());
                setMealDish.setDishId(d.getDishId());
                setMealDish.setDishName(d.getDishName());

                setMealDishes.add(setMealDish);
            });
        }

        return setMealService.update(setMeal,setMealDishes);
    }

    /**
     * 设置售卖状态
     * @param saleStateVO
     * @return
     */
    @ApiOperation(value = "套餐停/起售")
    @PutMapping("/updateStatus")
    public boolean updateStatus(@RequestBody SaleStatusVO saleStateVO){
        if(SystemCode.FORBIDDEN==saleStateVO.getStatus()){
            return setMealService.forbiddenSetMeals(saleStateVO.getIds());
        }

        if(SystemCode.ENABLED==saleStateVO.getStatus()){
            return setMealService.enableSetMeals(saleStateVO.getIds());
        }
        return false;
    }

    /**
     * 删除套餐
     * @param ids
     * @return
     */
    @ApiOperation(value = "删除套餐")
    @DeleteMapping("/delete")
    public boolean delete(@RequestBody List<String> ids){
        return setMealService.removeByIds(ids);
    }
}
