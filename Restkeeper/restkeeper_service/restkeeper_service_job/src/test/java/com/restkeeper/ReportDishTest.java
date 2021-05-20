package com.restkeeper;

import com.restkeeper.service.ReportDishService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ReportDishTest {

    @Autowired
    private ReportDishService reportDishService;

    @Test
    @Rollback(false)
    public void test(){
        reportDishService.generateData();
    }
}
