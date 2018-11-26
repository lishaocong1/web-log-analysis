package com.l000phone.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 参数工具类
 */
public class ParamUtils {
    /**
     * 从命令行参数中提取任务id
     */
    public static Long getTaskIdFromArgs(String[] args){
        if (args!=null&&args.length>0){
            return Long.valueOf(args[0]);
        }else {
            throw new RuntimeException("没有执行任务的id！请明示！");
        }
    }
    /**
     * 从json对象中提取参数
     */
    public static String getParam(String jsonStr,String field){
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        return jsonObject.getString(field);
    }
}
