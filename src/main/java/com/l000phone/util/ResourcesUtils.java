package com.l000phone.util;

import com.l000phone.constant.Constants;
import com.l000phone.constant.DeployMode;

import java.io.IOException;
import java.util.Properties;

public class ResourcesUtils {
    /**
     * 资源文件信息读取工具类
     */
  private static Properties properties;

    //部署模式
    public static DeployMode dMode;


    static {
        properties = new Properties();
        try {
            properties.load(ResourcesUtils.class.getClassLoader().getResourceAsStream("conf.properties"));

            //动态设置部署模式
            dMode = DeployMode.valueOf(getPropertyValueByKey(Constants.SPARK_JOB_DEPLOY_MODE).toUpperCase());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据key获得资源文件中的value
     *
     * @param key
     * @return
     */
    public static String getPropertyValueByKey(String key) {
        return properties.getProperty(key, "local");
    }
}
