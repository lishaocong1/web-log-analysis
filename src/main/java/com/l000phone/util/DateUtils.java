package com.l000phone.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期时间工具类
 */
public class DateUtils {
    public static final SimpleDateFormat TIME_FORMAT=
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final SimpleDateFormat DATE_FORMAT=
            new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat DATEKEY_FORMAT=
            new SimpleDateFormat("yyyyMMdd");
    /**
     * 判断一个时间是否在另一个时间之前
     * time1 第一个 时间
     * time2 第二个时间
     */
    public static boolean before(String time1,String time2){
        try {
            Date dataTime1 = TIME_FORMAT.parse(time1);
            Date dataTime2 = TIME_FORMAT.parse(time2);
            if (dataTime1.before(dataTime2)){
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return  false;
    }
    /**
     * 判断一个时间是否在另一个时间之后
     * time1 第一个时间
     * time2 第二个时间
     * return 判断结果
     */
    public static boolean after(String time1,String time2) {
        try {
            Date dateTime1 = TIME_FORMAT.parse(time1);
            Date dateTime2 = TIME_FORMAT.parse(time2);

            if (dateTime1.after(dateTime2)) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * 获取年月日和小时
     * datetime 时间(yyyy-MM-dd HH:mm:ss)
     * return 结果(yyyy-MM-dd_HH)
     */
    public static String getDateHour(String datetime){
        String date = datetime.split(" ")[0];
        String hourMinuteSecond = datetime.split(" ")[1];
        String hour = hourMinuteSecond.split(":")[0];
        return date+"_"+hour;
    }
    /**
     * 获取当天日期(yyyy-MM-dd)
     * @return 当天日期
     */
    public static String getTodayDate(){
        return DATE_FORMAT.format(new Date());
    }

    /**
     * 获取昨天的日期
     */
    public static String getYesterdayDate(){
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR,-1);
        Date date = cal.getTime();
        return  DATE_FORMAT.format(date);
    }
    /**
     * 格式化日期
     */
    public static String formatDate(Date date){
        return DATE_FORMAT.format(date);
    }
    /**
     * 格式化时间
     */
    public static String formatTime(Date date){
        return TIME_FORMAT.format(date);
    }
    /**
     * 解析时间字符串
     */
    public static Date parseTime(String time){
        try {
            return TIME_FORMAT.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * 格式化日期key
     */
    public static String formatDateKey(Date date){
        return DATEKEY_FORMAT.format(date);
    }
    /**
     * 将字符串解析为Date型的实例
     */
    public static  Date parseDateKey(String datekey){
        try {
            return DATEKEY_FORMAT.parse(datekey);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * 格式化时间,保留到分钟级别
     */
    public static String formatTimeMinute(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        return sdf.format(date);
    }
}
