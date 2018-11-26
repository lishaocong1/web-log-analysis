package com.l000phone.util;

/**
 * 检验工具类
 */
public class ValidationUtils {
    /**
     * 检验数据中的指定字段,是否在指定的范围内
     */
    public static boolean between(String data, String dataField,
                                  String parameter, String delimiter) {
        return data != null && data.trim().equals(StringUtils.getFieldFromConcatString(parameter, delimiter, dataField));
    }
}