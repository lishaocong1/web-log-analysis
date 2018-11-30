package com.l000phone.dao.session.impl;

import com.l000phone.bean.session.Top10Category;
import com.l000phone.dao.session.ITop10Category;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;

import java.sql.SQLException;

public class Top10CategoryImpl implements ITop10Category {
    private QueryRunner qr=new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public void saveBeanToDB(Top10Category bean) {
        String sql= "insert into top10_category values(?,?,?,?,?)";
        try {
            qr.update(sql,bean.getTask_id(),bean.getCategory_id(),bean.getClick_count(),bean.getOrder_count(),bean.getPay_count());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
