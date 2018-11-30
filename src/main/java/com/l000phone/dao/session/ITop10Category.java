package com.l000phone.dao.session;

import com.l000phone.bean.session.Top10Category;

/**
 * 操作特定品类点击,下单和支付总数对应的实体类的数据访问层
 */
public interface ITop10Category {
    /**
     * 将参数指定的实例保存到db中
     */
    void saveBeanToDB(Top10Category bean);
}
