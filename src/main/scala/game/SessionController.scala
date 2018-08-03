package game

import java.io.PrintWriter
import java.util.Date

import common.Bimap
import game.UserModel.User
import server.Server
import server.Server.{RequestGame, RequestLogin, RequestMatch, RequestRegister, executionContext}

import scala.collection.mutable
import scala.concurrent.Future

object SessionController {

  /**
    * Session
    *
    * @param sid  session id
    * @param uid  user id (email)
    * @param user user's information
    */
  final case class Session(sid: Int, uid: String, user: User)

  val StateWaiting = 0
  val StateMatching = 1
  val StateReady = 2
  val StatePlaying = 3

  final case class SessionState(state: Int, timestamp: Long, god: God)

  object SessionState {
    def apply(state: Int, god: God): SessionState = new SessionState(state, System.currentTimeMillis(), god)

    def apply(): SessionState = new SessionState(0, System.currentTimeMillis(), null)
  }

  val WaitingTime: Long = 1000 * 60 * 30

  // map: sid - uid
  val map = new Bimap[Int, String]()
  val states = new mutable.TreeMap[Int, SessionState]()

  /**
    * create a session or return an existing session for user
    *
    * @param uid user id
    * @return session id
    */
  def createSession(uid: String): Int = {
    val sid = s"${new Date().getTime}$uid".hashCode
    map.getA(uid).getOrElse {
      if (map.getB(sid).isEmpty) {
        this.synchronized {
          map.set(sid, uid)
          states += (sid -> SessionState())
        }
        sid
      } else {
        Thread.sleep(10)
        createSession(uid)
      }
    }
  }

  def routine(): Unit = Future {
    while (true) {
      Thread.sleep(WaitingTime*20)
      this.synchronized {
        states.dropWhile(item => item._2.timestamp < System.currentTimeMillis() - WaitingTime && item._2.state != StatePlaying)
      }
    }
  }

  val ghostGroup = Seq(Array(0), Array(1, 2), Array(3, 4, 5, 6),Array(7,8))

  def addGhost(): Unit = {
    for (arr <- ghostGroup) {
      val god = new God
      god.initialPlayer(arr)
      for (x <- arr) {
        states += (x -> SessionState(StatePlaying, god))
        map.set(x, "ghost@ghost.com")
      }
    }
  }

  def removeGhost(): Unit = {
    for (arr <- ghostGroup;
         sid <- arr) {
      try {
        states.remove(sid)
      } catch {
        case _:Throwable => {}
      }
    }
  }

  // api for http server

  /**
    * login
    *
    * @param req request
    */
  def login(req: RequestLogin): Option[Session] = {
    UserModel.login(req.email, req.password).flatMap { user =>
      val uid = user.uid
      val sid = createSession(uid)
      Some(Session(sid, uid, user))
    }
  }

  /**
    * register
    * call login after doing register
    *
    * @param req request
    */
  def register(req: RequestRegister): Option[Session] = {
    UserModel.register(req.email, req.nickname, req.avatar, req.gender, req.password).flatMap { _ =>
      login(RequestLogin(req.email, req.password))
    }
  }

  /**
    * matchPlayers
    */
  var fake_matching_count = 0

  def matchPlayers(req: RequestMatch): Future[Option[Int]] = Future {
    val player_count = 4

    def ready() = states.get(req.sid).exists(_.state == StateReady)

    states.get(req.sid).map { state =>
      this.synchronized {
        fake_matching_count += 1

        // try to start a game
        if (fake_matching_count >= player_count) {

          // todo: random
          val choosed_players = new Array[Int](player_count)

          var i = 1
          choosed_players(0) = req.sid
          for (player <- states) if (i < player_count && player._2.state == StateMatching) {
            choosed_players(i) = player._1
            i += 1
          }

          if (i == player_count) {
            val god = new God()
            fake_matching_count -= player_count
            god.initialPlayer(choosed_players)
            for (player_sid <- choosed_players) {
              states(player_sid) = SessionState(StateReady, god) // todo: maybe copy from the initial one
            }
            this.notifyAll()
          } else {
            states(req.sid) = state.copy(state = StateMatching)
            while (!ready()) {
              this.wait()
            }
          }
        } else {
          states(req.sid) = state.copy(state = StateMatching)
          while (!ready()) {
            this.wait()
          }
        }
      }
      player_count
    }
  }

  def gameRequest(req: RequestGame): Future[Option[String]] = Future {
    Server.log("game", req)
    states.get(req.sid) map { states =>
      Server.log("verbose game in", req.toString)
      val god = states.god
      val result =
        try {
          god.request(req.sid, req.msg)
        } catch {
          case ex: Exception => {
            ex.printStackTrace(new PrintWriter(Server.log_file))
            "Internal Error"
          }
        }
      Server.log("verbose game out", result)
      result
    }
  }
}
