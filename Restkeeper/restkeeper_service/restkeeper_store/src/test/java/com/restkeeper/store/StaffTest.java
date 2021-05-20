package com.restkeeper.store;

import com.restkeeper.store.entity.Staff;
import com.restkeeper.store.service.IStaffService;
import org.apache.dubbo.config.annotation.Reference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class StaffTest extends BaseTest{

    @Reference(version = "1.0.0",check = false)
    private IStaffService staffService;

    @Test
    @Rollback(false)
    public void add(){
        Staff staff = new Staff();
        staff.setStaffName("demo");
        staff.setPassword("123456");
        staff.setPhone("15688888888");
        staff.setSex("男");
        staff.setIdNumber("48461865487846");
        staffService.addStaff(staff);
    }
}
