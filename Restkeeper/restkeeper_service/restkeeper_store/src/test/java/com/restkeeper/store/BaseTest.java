package com.restkeeper.store;

import com.restkeeper.tenant.TenantContext;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@RunWith(SpringRunner.class)
public class BaseTest {

    @Before
    public void init(){
        Map<String,Object> map = new HashMap<>();
        map.put("shopId","test");
        map.put("storeId","test");
        map.put("userType",2);//门店管理员
        TenantContext.addAttachments(map);
    }
}
