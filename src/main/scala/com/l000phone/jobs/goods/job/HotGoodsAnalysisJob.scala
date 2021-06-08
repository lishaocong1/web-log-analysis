package com.l000phone.jobs.goods.job

import com.alibaba.fastjson.JSON
import com.l000phone.bean.common.{Task, TaskParam}
import com.l000phone.bean.goods.{CityInfo, ExtendInfo, HotGoodsInfo}
import com.l000phone.dao.common.ITaskDao
import com.l000phone.dao.common.impl.TaskDaoImpl
import com.l000phone.dao.goods.{ICityInfoDao, IHotGoodsInfoDao}
import com.l000phone.dao.goods.impl.{CityInfoImpl, HotGoodsInfoDaoImpl}
import com.l000phone.mock.MockData
import com.l000phone.util.ResourcesUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession.Builder
import org.apache.spark.sql.{Row, SparkSession}

/**
  * 热门商品离线统计
  */
object HotGoodsAnalysisJob {
  def main(args: Array[String]): Unit = {
    //前提：
    //哈哈哈
    val spark: SparkSession = prepareOperate(args)
    

    //步骤：
    //①Spark作业接收taskid，查询对应的MySQL中的task，获取用户指定的筛选参数；
    filterSessionByCondition(spark, args)

    //②统计出指定日期范围内的，各个区域的top3热门商品；
    val rdd: RDD[Row] = calPerAreaTop3(args, spark)


    //③最后将结果写入MySQL表中。
   saveResultToDB(rdd, spark, args)

    //4、资源释放
    spark.stop()
  }
  /**
    * 准备操作
    *
    * @param args
    * @return
    */
  private def prepareOperate(args: Array[String]) = {
    //0、拦截非法的操作
    if (args == null || args.length != 1) {
      println("参数录入错误或是没有准备参数！请使用：spark-submit 主类  jar taskId")
      System.exit(-1)
    }

    //1、SparkSession的实例(注意：若分析的是hive表，需要启用对hive的支持，Builder的实例.enableHiveSupport())
    val builder: Builder = SparkSession.builder().appName(HotGoodsAnalysisJob.getClass.getSimpleName)

    //若是本地集群模式，需要单独设置
    if (ResourcesUtils.dMode.toString.toLowerCase().equals("local")) {
      builder.master("local[*]")
    }

    val spark: SparkSession = builder.getOrCreate()


    //2、将模拟的数据装载进内存（hive表中的数据）
    MockData.mock(spark.sparkContext, spark.sqlContext)

    //3、设置日志的显示级别
    spark.sparkContext.setLogLevel("WARN")

    //模拟数据测试：
    //spark.sql("select * from user_visit_action").show(1000)

    //4、返回SparkSession的实例
    spark
  }

  /**
    * 按条件筛选session
    */
  def filterSessionByCondition(spark: SparkSession, args: Array[String]) = {
    //①准备一个字符串构建器的实例StringBuffer，用于存储sql
    val buffer = new StringBuffer
    buffer.append("select i.city,u.click_product_id from  user_visit_action u,user_info i where u.user_id=i.user_id and u.click_product_id is not null ")
    //②根据从mysql中task表中的字段task_param查询到的值，进行sql语句的拼接
    val taskId = args(0).toInt
    val taskDao: ITaskDao = new TaskDaoImpl
    val task: Task = taskDao.findTaskById(taskId)

    // task_param={"ages":[0,100],"genders":["男","女"],"professionals":["教师", "工人", "记者", "演员", "厨师", "医生", "护士", "司机", "军人", "律师"],"cities":["南京", "无锡", "徐州", "常州", "苏州", "南通", "连云港", "淮安", "盐城", "扬州"]})
    val taskParamJsonStr = task.getTask_param();

    //使用FastJson，将json对象格式的数据封装到实体类TaskParam中
    val taskParam: TaskParam = JSON.parseObject[TaskParam](taskParamJsonStr, classOf[TaskParam])

    //获得参数值
    val start_time = taskParam.getStart_time
    val end_time = taskParam.getEnd_time

    //start_time
    if (start_time != null) {
      buffer.append(" and u.action_time>='").append(start_time).append("'")
    }

    //end_time
    if (end_time != null) {
      buffer.append(" and u.action_time<='").append(end_time).append("'")
    }

    //③将结果注册为一张临时表
    spark.sql(buffer.toString).createOrReplaceTempView("filter_after_action")
    spark.sqlContext.cacheTable("filter_after_action")

    //测试
    //spark.sql("select * from filter_after_action").show(2000)
  }

  /**
    * 统计出指定日期范围内的,各个区域的top3热门商品
    */
  def calPerAreaTop3(args: Array[String], spark: SparkSession) = {
    //a)将mysql中的城市信息表city_info映射为内存中的一张临时表
    val dao: ICityInfoDao = new CityInfoImpl
    val cityInfos: java.util.List[CityInfo] = dao.findAllInfos
    spark.createDataFrame(cityInfos,classOf[CityInfo]).createOrReplaceTempView("city_info")
    //测试
    //spark.sql("select * from city_info").show(100)

    //b) 将三张表city_info表（转化之后的临时表），product_info表，filter_after_action表进行内连接查询
    //注册自定义函数
    spark.udf.register("getAreaLevel", (area: String) => getAreaLevelFunction(area))
    spark.udf.register("getProductStatus", (extend_info: String) => getProductStatusFunction(extend_info))

    val sqlBuilder: StringBuilder = new StringBuilder
    sqlBuilder.append("select c.area,p.product_id,c .city_name,p.product_name,concat(c.area,'_',p.product_id) a_pid, getAreaLevel(c.area) area_level,getProductStatus(p.extend_info) product_status  from city_info c,product_info p,filter_after_action f ")
    sqlBuilder.append("where c.city_name=f.city and p.product_id=f.click_product_id ")

    spark.sql(sqlBuilder.toString()).createOrReplaceTempView("temp_city_product_filter")
    spark.sqlContext.cacheTable("temp_city_product_filter")

    //c)分析三张表查询后的结果
    //注意：若一个sql语句中进行了聚合操作（如：分组），select之后的字段要么是：聚合函数，要么是分组的依据字段
    //清空字符串构建器，
    sqlBuilder.delete(0, sqlBuilder.length)
    sqlBuilder.append("select ")
    sqlBuilder.append("concat_ws(',', collect_set(distinct area)) area,")
    sqlBuilder.append("concat_ws(',', collect_set(distinct product_id)) product_id,")
    sqlBuilder.append("concat_ws(',', collect_set(distinct area_level)) area_level,")
    sqlBuilder.append("concat_ws(',', collect_set(distinct city_name)) city_names,")
    sqlBuilder.append("count(*) click_count,")
    sqlBuilder.append("concat_ws(',', collect_set(distinct product_name)) product_name,")
    sqlBuilder.append("concat_ws(',', collect_set(distinct product_status)) product_status ")
    sqlBuilder.append("from temp_city_product_filter group by a_pid")

    spark.sql(sqlBuilder.toString).createOrReplaceTempView("temp_city_product_filter_aggr")
    spark.sqlContext.cacheTable("temp_city_product_filter_aggr")

    //d)对各个地区的热门商品分组求top3
    val rdd: RDD[Row] = spark.sql("select *,row_number() over( partition by area order by click_count desc) level from temp_city_product_filter_aggr having level<=3").rdd

    //e)返回结果
    rdd
  }
  /**
    * 根据产品的扩展信息获取产品的真实的状态名，如：0~>自营；1~>第三方
    *
    * @param extend_info ，{"product_status": 0}
    */
  def getProductStatusFunction(extend_info: String) = {
    val bean = JSON.parseObject(extend_info, classOf[ExtendInfo])
    val statusCode = bean.getProduct_status
    statusCode match {
      case 0 => "自营"
      case _ => "第三方"
    }
  }

  /**
    * 根据地区名动态获得地区级别的自定义函数
    *
    * 华东大区，A级，华中大区，B级，东北大区，C级，西北大区，D级
    *
    * @param area
    */
  def getAreaLevelFunction(area: String) = area match {
    case "华东大区" => "A级"
    case "华中大区" => "B级"
    case "东北大区" => "C级"
    case "西北大区" => "D级"
    case _ => "E级"
  }

  /**
    * 将最后的结果写入到MySQL表中
    */
  def saveResultToDB(rdd: RDD[Row], spark: SparkSession, args: Array[String]) = {
    val bcTaskId = spark.sparkContext.broadcast[Int](args(0).toInt)

    rdd.foreachPartition(itr => {
      if (!itr.isEmpty) {
        val dao: IHotGoodsInfoDao = new HotGoodsInfoDaoImpl
        val beans: java.util.List[HotGoodsInfo] = new java.util.LinkedList

        itr.foreach(row => {
          val task_id = bcTaskId.value
          val area = row.getAs[String]("area")
          val product_id = row.getAs[String]("product_id").toInt
          val area_level = row.getAs[String]("area_level")
          val city_names = row.getAs[String]("city_names")
          val click_count = row.getAs[Long]("click_count").toInt
          val product_name = row.getAs[String]("product_name")
          val product_status = row.getAs[String]("product_status")

          val bean = new HotGoodsInfo(task_id: Int, area: String, area_level: String, product_id: Int, city_names: String, click_count: Int, product_name: String, product_status: String)

          beans.add(bean)
        })

        //保存到db中
        dao.saveBeansToDB(beans)
      }
    })
  }
}
