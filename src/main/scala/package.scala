package edu.luc.cs.metrics.defect.density.service

import reactivemongo.api.MongoDriver

/**
 * Created by shilpika on 8/1/15.
 */
object `package` {
  val mongoDriver = new MongoDriver
  val mongoConnection = mongoDriver.connection(List("localhost"))

}
