package com.l000phone.dao.page.impl;

import com.l000phone.bean.page.PageSplitConvertRate;
import com.l000phone.dao.page.IPageSplitConvertRateDao;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;

import java.sql.SQLException;

public class PageSplitConvertRateDaoImpl implements IPageSplitConvertRateDao {
    private QueryRunner qr = new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public void saveOrUpdate(PageSplitConvertRate bean) {
        try {
            PageSplitConvertRate beanTmp = qr.query("select * from page_split_convert_rate where task_id=?", new BeanHandler<>(PageSplitConvertRate.class), bean.getTask_id());
            if (beanTmp == null) {
                qr.update("insert into page_split_convert_rate values(?,?)", bean.getTask_id(), bean.getConvert_rate());
            } else {
                qr.update("update page_split_convert_rate set convert_rate=? where task_id=?", bean.getConvert_rate(), bean.getTask_id());
            }


        } catch (SQLException e) {
            throw new RuntimeException("保存或是更新page_split_convert_rate的时候发生异常了哦！。。。");
        }
    }
}
