package com.l000phone.dao.session.impl;

import com.l000phone.bean.session.Top10CategorySession;
import com.l000phone.dao.session.ITop10CategorySession;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;

import java.sql.SQLException;

public class Top10CategorySessionImpl implements ITop10CategorySession {
    private QueryRunner qr=new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public void saveToDB(Top10CategorySession bean) {
        String sql="insert into top10_category_session values(?,?,?,?)";
        try {
            qr.update(sql,bean.getTask_id(),bean.getCategory_id(),bean.getSession_id(),bean.getClick_count());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
