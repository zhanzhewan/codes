package com.restkeeper.shop;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.restkeeper.shop.entity.StoreManager;
import com.restkeeper.shop.service.IStoreManagerService;
import org.apache.dubbo.config.annotation.Reference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@RunWith(SpringRunner.class)
public class StoreMangerTest extends BaseTest{

    @Reference(version = "1.0.0",check = false)
    private IStoreManagerService storeManagerService;

    @Test
    public void queryPageByCriteria(){
        IPage<StoreManager> page = storeManagerService.queryPageByCriteria(1, 10, "test");
        List<StoreManager> storeManagerList = page.getRecords();
        System.out.println(storeManagerList);
    }

    @Test
    @Rollback(false)
    public void add(){
        List<String> storeIds = new ArrayList<>();
        storeIds.add("1206216301732823041");
        storeManagerService.addStoreManager("lisi","15666666666",storeIds);
    }

    @Test
    @Rollback(false)
    public void updateStoreManger(){
        List<String> storeIds = new ArrayList<>();
        storeIds.add("1206476527887405057");
        storeIds.add("1206476814614208513");
        storeIds.add("1206477268886712321");
        storeManagerService.updateStoreManager("1205130634617622530","test1","15122223333",storeIds);
    }

    @Test
    @Rollback(false)
    public void pauseStoreManager(){
        storeManagerService.pauseStoreManager("1210551522631430145");
    }

    @Test
    @Rollback(false)
    public void deleteStoreManager(){
        storeManagerService.deleteStoreManager("1210551522631430145");
    }

}
