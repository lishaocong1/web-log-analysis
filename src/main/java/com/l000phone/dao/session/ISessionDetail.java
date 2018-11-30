package com.l000phone.dao.session;

import com.l000phone.bean.session.SessionDetail;

/**
 * 操作session的明细数据 数据访问层的接口
 */
public interface ISessionDetail {
    /**
     * 将SessionDetail实例保存到db中
     */
    void saveToDB(SessionDetail bean);
}
