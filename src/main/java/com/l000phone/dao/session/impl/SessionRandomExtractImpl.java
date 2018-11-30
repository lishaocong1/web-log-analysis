package com.l000phone.dao.session.impl;

import com.l000phone.bean.session.SessionRandomExtract;
import com.l000phone.dao.session.ISessionRandomExtract;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;

import java.sql.SQLException;
import java.util.List;

public class SessionRandomExtractImpl implements ISessionRandomExtract {
    private QueryRunner qr=new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public void saveBeansToDB(List<SessionRandomExtract> beans) {
        String sql = "insert into session_random_extract values(?,?,?,?,?)";
        Object[][]params=new Object[beans.size()][];
        //{{1,"","",""},{}}
        for (int i=0;i<params.length;i++){
            SessionRandomExtract bean = beans.get(i);
            params[i]=new Object[]{bean.getTask_id(),bean.getSession_id(),bean.getStart_time(),bean.getEnd_time(),bean.getSearch_keywords()};
        }
        try {
            qr.batch(sql,params);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
