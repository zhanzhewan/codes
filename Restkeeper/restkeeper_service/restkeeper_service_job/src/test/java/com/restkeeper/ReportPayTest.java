package com.restkeeper;

import com.restkeeper.service.ReportPayService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ReportPayTest {

    @Autowired
    private ReportPayService reportPayService;

    @Test
    @Rollback(false)
    public void test(){
        reportPayService.generateData();
    }
}
