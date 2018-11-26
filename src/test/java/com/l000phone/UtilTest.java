package com.l000phone;
import com.l000phone.constant.Constants;
import com.l000phone.util.DBCPUtil;
import com.l000phone.util.ResourcesUtils;
import org.junit.Test;

import java.sql.Connection;

/**
 * Description：工具类测试<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 * Date：2018年11月26日
 *
 * @author 徐文波
 * @version : 1.0
 */
public class UtilTest {

    @Test
    public void testResourceUtil(){
        String mode = ResourcesUtils.getPropertyValueByKey(Constants.SPARK_JOB_DEPLOY_MODE);
        System.out.println("部署模式是："+mode);
    }

    @Test
    public void testDBCPUtil(){
        Connection conn = DBCPUtil.getConnection();
        System.out.println(conn);
    }
}
