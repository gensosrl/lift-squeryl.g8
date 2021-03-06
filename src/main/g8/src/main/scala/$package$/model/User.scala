package $package$
package model


import org.joda.time.DateTime

import net.liftweb._
import common._
import http.{StringField => _, BooleanField => _, _}
import record.field._
import util.FieldContainer

import net.liftmodules.squerylauth._
import net.liftmodules.squerylauth.field._
import net.liftmodules.squerylauth.model._
import net.liftmodules.squerylauth.lib._
import net.liftweb.record.MetaRecord
import net.liftweb.util.Helpers._
import org.squeryl.Table
import net.liftweb.squerylrecord.RecordTypeMode._

class User private () extends ProtoAuthUser[User] {
  def meta = User

  val idField = new LongField(this)

  val locale = new LocaleField(this) {
    override def displayName = "Locale"
    override def defaultValue = "en_US"
  }

  val timezone = new TimeZoneField(this) {
    override def displayName = "Time Zone"
    override def defaultValue = "America/Chicago"
  }

  val name = new StringField(this, 64) {
    override def displayName = "Name"

    override def validations =
      valMaxLen(64, "Name must be 64 characters or less") _ ::
      super.validations
  }

  val location = new StringField(this, 64) {
    override def displayName = "Location"

    override def validations =
      valMaxLen(64, "Location must be 64 characters or less") _ ::
      super.validations
  }

  val bio = new TextareaField(this, 160) {
    override def displayName = "Bio"

    override def validations =
      valMaxLen(160, "Bio must be 160 characters or less") _ ::
      super.validations
  }
  
  lazy val roles = DbSchema.rolesToUsers.right(this)
  
  /*
   * FieldContainers for various LiftScreeens.
   */
  def accountScreenFields = new FieldContainer {
    def allFields = List(username, email, locale, timezone)
  }

  def profileScreenFields = new FieldContainer {
    def allFields = List(name, location, bio)
  }

  def registerScreenFields = new FieldContainer {
    def allFields = List(username, email)
  }

  def findAllByUsername(username: String): List[User] = meta.findAllByUsername(username)

  def findAllByEmail(email: String): List[User] = meta.findAllByEmail(email)

}

object User extends User with MetaRecord[User] with ProtoAuthUserMeta[User] with Loggable {

  def findByEmail(in: String): Box[User] = inTransaction {
    DbSchema.users.where(user => user.email === in).headOption
  }
  
  def findByUsername(in: String): Box[User] = inTransaction {
    DbSchema.users.where(user => user.username === in).headOption
  }

  def find(id: Long): Box[User] = DbSchema.users.lookup(id)

  def save(inst: User) = DbSchema.users.insertOrUpdate(inst)
 
  def findByStringId(id: String): Box[User] =
    asLong(id) match {
      case Full(i) => find(i)
      case _ => Empty
    }

  override def onLogIn: List[User => Unit] = List(user => User.loginCredentials.remove())
  override def onLogOut: List[Box[User] => Unit] = List(
    x => logger.debug("User.onLogOut called."),
    boxedUser => boxedUser.foreach { u =>
      ExtSession.deleteExtCookie()
    }
  )

  /*
   * SquerylAuth vars
   */
  private lazy val siteName = SquerylAuth.siteName.vend
  private lazy val sysUsername = SquerylAuth.systemUsername.vend
  private lazy val indexUrl = SquerylAuth.indexUrl.vend
  private lazy val registerUrl = SquerylAuth.registerUrl.vend
  private lazy val loginTokenAfterUrl = SquerylAuth.loginTokenAfterUrl.vend

  /*
   * LoginToken
   */
  override def handleLoginToken: Box[LiftResponse] = {
    val resp = S.param("token").flatMap(LoginToken.findByStringId) match {
      case Full(at) if (at.expires.isExpired) => {
        LoginToken.delete_!(at)
        RedirectWithState(indexUrl, RedirectState(() => { S.error("Login token has expired") }))
      }
      case Full(at) => find(at.userId.get).map(user => {
        if (user.validate.length == 0) {
          user.verified(true)
          User.save(user)
          logUserIn(user)
          LoginToken.delete_!(at)
          RedirectResponse(loginTokenAfterUrl)
        }
        else {
          LoginToken.delete_!(at)
          regUser(user)
          RedirectWithState(registerUrl, RedirectState(() => { S.notice("Please complete the registration form") }))
        }
      }).openOr(RedirectWithState(indexUrl, RedirectState(() => { S.error("User not found") })))
      case _ => RedirectWithState(indexUrl, RedirectState(() => { S.warning("Login token not provided") }))
    }

    Full(resp)
  }

  // send an email to the user with a link for logging in
  def sendLoginToken(user: User): Unit = {
    import net.liftweb.util.Mailer._

    val token = LoginToken.createForUserId(user.id)

    val msgTxt =
      """
        |Someone requested a link to change your password on the %s website.
        |
        |If you did not request this, you can safely ignore it. It will expire 48 hours from the time this message was sent.
        |
        |Follow the link below or copy and paste it into your internet browser.
        |
        |%s
        |
        |Thanks,
        |%s
      """.format(siteName, token.url, sysUsername).stripMargin

    sendMail(
      From(SquerylAuth.systemFancyEmail),
      Subject("%s Password Help".format(siteName)),
      To(user.fancyEmail),
      PlainMailBodyType(msgTxt)
    )
  }

  /*
   * ExtSession
   */
  def createExtSession(uid: Long) = ExtSession.createExtSession(uid)

  /*
  * Test for active ExtSession.
  */
  def testForExtSession: Box[Req] => Unit = {
    ignoredReq => {
      if (currentUserId.isEmpty) {
        ExtSession.handleExtSession match {
          case Full(es) => find(es.userId.get).foreach { user => logUserIn(user, false) }
          case Failure(msg, _, _) =>
            logger.warn("Error logging user in with ExtSession: %s".format(msg))
          case Empty =>
        }
      }
    }
  }
  
  override def findAllByUsername(username: String): List[User] = DbSchema.users.where(_.username === username).toList
  
  override def findAllByEmail(email: String): List[User] = DbSchema.users.where(_.email === email).toList

  // used during login process
  object loginCredentials extends SessionVar[LoginCredentials](LoginCredentials(""))
  object regUser extends SessionVar[User](createRecord.email(loginCredentials.is.email))
}

case class LoginCredentials(email: String, isRememberMe: Boolean = false)

object SystemUser {
  private val username = "$name;format="norm"$"
  private val email = "$admin_email$"

  lazy val user: User = User.findByUsername(username) openOr {
    User.save(User.createRecord
      .name("$name$")
      .username(username)
      .email(email)
      .locale("en_US")
      .timezone("America/Chicago")
      .verified(true)
      .password("$admin_password$", true)
    )
  }
  
}

