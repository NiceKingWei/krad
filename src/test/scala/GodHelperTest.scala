package game

import GodHelper._
import spray.json._

object GodHelperTest {
  def main(args: Array[String]): Unit = {
    val x = MsgChooseHero("sfaf")
    val s = x.toJson.toString // JsValue
    println(s)
    println(s.parseJson.convertTo[MsgChooseHero])
  }
}
