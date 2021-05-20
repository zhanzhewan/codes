package com.restkeeper.controller;

import com.google.common.collect.Lists;
import com.restkeeper.entity.DishEs;
import com.restkeeper.entity.SearchResult;
import com.restkeeper.response.vo.PageVO;
import com.restkeeper.service.IDishSearchService;
import com.restkeeper.store.service.ISellCalculationService;
import com.restkeeper.vo.DishPanelVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = {"菜品搜索相关接口"})
@RestController
@RequestMapping("/dish")
public class DishController {

    @Reference(version = "1.0.0",check = false)
    private IDishSearchService dishSearchService;

    @Reference(version = "1.0.0",check = false)
    private ISellCalculationService sellCalculationService;

    @ApiOperation("根据商品编码完成商品搜索")
    @GetMapping("/queryByCode/{code}/{page}/{pageSize}")
    public PageVO<DishPanelVO> queryByCode(@PathVariable String code, @PathVariable int page, @PathVariable int pageSize){

        PageVO<DishPanelVO> pageResult = new PageVO<>();

        SearchResult<DishEs> searchResult = dishSearchService.searchDishByCode(code, page, pageSize);

        pageResult.setCounts(searchResult.getTotal());
        pageResult.setPage(page);
        long pageCount = searchResult.getTotal()%pageSize==0?searchResult.getTotal()/pageSize : searchResult.getTotal()/pageSize+1;
        pageResult.setPages(pageCount);

        List<DishPanelVO> dishVOList = Lists.newArrayList();

        searchResult.getRecords().forEach(es->{


            DishPanelVO dishPanelVO = new DishPanelVO();

            dishPanelVO.setDishId(es.getId());
            dishPanelVO.setDishName(es.getDishName());
            dishPanelVO.setPrice(es.getPrice());
            dishPanelVO.setImage(es.getImage());
            dishPanelVO.setType(es.getType());
            dishPanelVO.setRemainder(sellCalculationService.getRemainderCount(es.getId()));

            dishVOList.add(dishPanelVO);
        });

        pageResult.setItems(dishVOList);

        return pageResult;
    }
}
