val p = List(1,2,3,4).toIterator
val b = p.duplicate

b._1.indexOf(9)

print(p.toList)
print(b._1.toList)
print(b._2.toList)


val a = new java.util.Hashtable[String, String]
a.contains("a")
a.put("a","aaaa")
a.get("a")
if(a.contains("a")){

}

val s1 = "{ \"_id\" : { \"$oid\" : \"561a09a97787df37bd32bae8\"} , \"id\" : \"1\" , ".length

/*val auu= java.util.UUID.nameUUIDFromBytes()
val cuu = java.util.UUID.fromString("aaa")*/


/*import reactivemongo.api.MongoDriver
import scala.concurrent.ExecutionContext.Implicits._
import reactivemongo.api.collections.bson._
val mongoDriver = new MongoDriver
val mongoConnection = mongoDriver.connection(List("localhost"))
val db = mongoConnection.db("sshilpika_metrics-test_master")
val coll = db.collection[BSONCollection]("_travis_yml")*/


/*import java.time._
val ins = Instant.parse("2015-02-15T09:33:30Z")
val ins1 = Instant.parse("2015-02-28T23:59:59Z")
val inss = Instant.parse("2015-02-01T00:00:00Z")
val t1 = java.time.Duration.between(ins,ins1).toMillis.toDouble/1000
val tALL = java.time.Duration.between(inss,ins1).toMillis.toDouble/1000
val res1 = t1/tALL

val ins2 = Instant.parse("2015-02-15T11:34:12Z")
val t2 = java.time.Duration.between(ins2,ins1).toMillis.toDouble/1000
val res2 = (t2*56)/tALL

val Res = res1+res2*/
