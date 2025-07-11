package net.mjstudio.rnkakao.user

import android.content.ActivityNotFoundException
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.auth.model.Prompt.CERT
import com.kakao.sdk.auth.model.Prompt.CREATE
import com.kakao.sdk.auth.model.Prompt.LOGIN
import com.kakao.sdk.auth.model.Prompt.SELECT_ACCOUNT
import com.kakao.sdk.auth.model.Prompt.UNIFY_DAUM
import com.kakao.sdk.user.UserApiClient
import net.mjstudio.rnkakao.core.util.RNCKakaoResponseNotFoundException
import net.mjstudio.rnkakao.core.util.RNCKakaoUtil
import net.mjstudio.rnkakao.core.util.argArr
import net.mjstudio.rnkakao.core.util.argMap
import net.mjstudio.rnkakao.core.util.filterIsInstance
import net.mjstudio.rnkakao.core.util.onMain
import net.mjstudio.rnkakao.core.util.pushMapList
import net.mjstudio.rnkakao.core.util.pushStringList
import net.mjstudio.rnkakao.core.util.putB
import net.mjstudio.rnkakao.core.util.putD
import net.mjstudio.rnkakao.core.util.putI
import net.mjstudio.rnkakao.core.util.putL
import net.mjstudio.rnkakao.core.util.rejectWith
import net.mjstudio.rnkakao.core.util.unix
import java.util.Date

class RNCKakaoUserModule internal constructor(
  context: ReactApplicationContext,
) : KakaoUserSpec(context) {
  override fun getName(): String = NAME

  @ReactMethod
  override fun login(
    serviceTerms: ReadableArray?,
    prompts: ReadableArray?,
    useKakaoAccountLogin: Boolean,
    scopes: ReadableArray?,
    nonce: String?,
    promise: Promise,
  ) = onMain {
    // Debug: Log nonce parameter
    android.util.Log.d("RNCKakaoUser", "Login called with nonce: $nonce")
    
    val context =
      currentActivity ?: run {
        promise.rejectWith(ActivityNotFoundException())
        return@onMain
      }
    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
      if (error != null) {
        promise.rejectWith(error)
      } else if (token == null) {
        promise.rejectWith(RNCKakaoResponseNotFoundException("token"))
      } else {
        // Debug: Log received ID token
        android.util.Log.d("RNCKakaoUser", "Received ID token: ${token.idToken}")
        
        promise.resolve(
          argMap().apply {
            putString("accessToken", token.accessToken)
            putString("refreshToken", token.refreshToken)
            putString("tokenType", null)
            putString("idToken", token.idToken)
            putDouble(
              "accessTokenExpiresAt",
              token.accessTokenExpiresAt.unix.toDouble(),
            )
            putDouble(
              "refreshTokenExpiresAt",
              token.refreshTokenExpiresAt.unix.toDouble(),
            )
            putDouble(
              "accessTokenExpiresIn",
              RNCKakaoUtil.diffSec(token.accessTokenExpiresAt, Date()).toDouble(),
            )
            putDouble(
              "refreshTokenExpiresIn",
              RNCKakaoUtil.diffSec(token.refreshTokenExpiresAt, Date()).toDouble(),
            )
            putArray(
              "scopes",
              argArr().apply {
                pushStringList(token.scopes ?: listOf())
              },
            )
          },
        )
      }
    }

    if (scopes?.filterIsInstance<String>()?.isEmpty() == false) {
      android.util.Log.d("RNCKakaoUser", "Calling loginWithNewScopes with nonce: $nonce")
      UserApiClient.instance.loginWithNewScopes(
        context,
        scopes = scopes.filterIsInstance<String>(),
        nonce = nonce,
        callback = callback,
      )
    } else if (
      UserApiClient.instance.isKakaoTalkLoginAvailable(context) &&
      !useKakaoAccountLogin &&
      scopes
        ?.filterIsInstance<String>()
        ?.isEmpty() == true
    ) {
      android.util.Log.d("RNCKakaoUser", "Calling loginWithKakaoTalk with nonce: $nonce")
      UserApiClient.instance.loginWithKakaoTalk(
        context,
        serviceTerms = serviceTerms?.filterIsInstance<String>()?.ifEmpty { null },
        nonce = nonce,
        callback = callback,
      )
    } else {
      android.util.Log.d("RNCKakaoUser", "Calling loginWithKakaoAccount with nonce: $nonce")
      UserApiClient.instance.loginWithKakaoAccount(
        context,
        prompts =
          prompts
            ?.filterIsInstance<String>()
            ?.mapNotNull {
              when (it) {
                "Login" -> LOGIN
                "Create" -> CREATE
                "Cert" -> CERT
                "UnifyDaum" -> UNIFY_DAUM
                "SelectAccount" -> SELECT_ACCOUNT
                else -> null
              }
            }?.ifEmpty { null },
        serviceTerms = serviceTerms?.filterIsInstance<String>()?.ifEmpty { null },
        nonce = nonce,
        callback = callback,
      )
    }
  }

  @ReactMethod
  override fun isKakaoTalkLoginAvailable(promise: Promise) =
    onMain {
      promise.resolve(UserApiClient.instance.isKakaoTalkLoginAvailable(reactApplicationContext))
    }

  @ReactMethod
  override fun logout(promise: Promise) =
    onMain {
      UserApiClient.instance.logout {
        if (it != null) {
          promise.rejectWith(it)
        } else {
          promise.resolve(42)
        }
      }
    }

  @ReactMethod
  override fun unlink(promise: Promise) =
    onMain {
      UserApiClient.instance.unlink {
        if (it != null) {
          promise.rejectWith(it)
        } else {
          promise.resolve(42)
        }
      }
    }

  @ReactMethod
  override fun isLogined(promise: Promise) =
    onMain {
      if (AuthApiClient.instance.hasToken()) {
        UserApiClient.instance.accessTokenInfo { _, error ->
          if (error != null) {
            promise.rejectWith(error)
          } else {
            promise.resolve(true)
          }
        }
      } else {
        promise.resolve(false)
      }
    }

  @ReactMethod
  override fun scopes(
    scopes: ReadableArray?,
    promise: Promise,
  ) = onMain {
    UserApiClient.instance.scopes { scopeInfo, error ->
      if (error != null) {
        promise.rejectWith(error)
      } else if (scopeInfo?.scopes == null) {
        promise.rejectWith(RNCKakaoResponseNotFoundException("scopes"))
      } else {
        promise.resolve(
          argArr().also { arr ->
            scopeInfo.scopes!!.forEach { scope ->
              arr.pushMap(
                argMap().also { map ->
                  map.putString("id", scope.id)
                  map.putString("displayName", scope.displayName)
                  map.putString("type", scope.type.name)
                  map.putBoolean("using", scope.using)
                  map.putB("delegated", scope.delegated)
                  map.putBoolean("agreed", scope.agreed)
                  map.putB("revocable", scope.revocable)
                },
              )
            }
          },
        )
      }
    }
  }

  @ReactMethod
  override fun revokeScopes(
    scopes: ReadableArray,
    promise: Promise,
  ) = onMain {
    UserApiClient.instance.revokeScopes(
      scopes = scopes.filterIsInstance<String>(),
    ) { scopeInfo, error ->
      if (error != null) {
        promise.rejectWith(error)
      } else if (scopeInfo == null) {
        promise.rejectWith(RNCKakaoResponseNotFoundException("scopeInfo"))
      } else {
        promise.resolve(42)
      }
    }
  }

  @ReactMethod
  override fun serviceTerms(promise: Promise) =
    onMain {
      UserApiClient.instance.serviceTerms { serviceTerms, error ->
        if (error != null) {
          promise.rejectWith(error)
        } else if (serviceTerms?.serviceTerms == null) {
          promise.rejectWith(RNCKakaoResponseNotFoundException("serviceTerms"))
        } else {
          promise.resolve(
            argArr().pushMapList(
              serviceTerms.serviceTerms?.map { term ->
                argMap().apply {
                  putString("tag", term.tag)
                  putD("agreedAt", term.agreedAt?.unix?.toDouble())
                  putBoolean("agreed", term.agreed)
                  putBoolean("required", term.required)
                  putBoolean("revocable", term.revocable)
                }
              } ?: listOf(),
            ),
          )
        }
      }
    }

  @ReactMethod
  override fun shippingAddresses(promise: Promise) =
    onMain {
      UserApiClient.instance.shippingAddresses { addrs, error ->
        if (error != null) {
          promise.rejectWith(error)
        } else if (addrs == null) {
          promise.rejectWith(RNCKakaoResponseNotFoundException("shippingAddresses"))
        } else {
          promise.resolve(
            argMap().apply {
              putD("userId", addrs.userId?.toDouble())
              putB("needsAgreement", addrs.needsAgreement)
              putArray(
                "shippingAddresses",
                argArr().apply {
                  pushMapList(
                    addrs.shippingAddresses?.map { addr ->
                      argMap().apply {
                        putDouble("id", addr.id.toDouble())
                        putString("name", addr.name)
                        putBoolean("isDefault", addr.isDefault)
                        putD("updatedAt", addr.updatedAt?.unix?.toDouble())
                        putString("type", addr.type?.name)
                        putString("baseAddress", addr.baseAddress)
                        putString("detailAddress", addr.detailAddress)
                        putString("receiverName", addr.receiverName)
                        putString("receiverPhoneNumber1", addr.receiverPhoneNumber1)
                        putString("receiverPhoneNumber2", addr.receiverPhoneNumber2)
                        putString("zoneNumber", addr.zoneNumber)
                        putString("zipCode", addr.zipCode)
                      }
                    } ?: listOf(),
                  )
                },
              )
            },
          )
        }
      }
    }

  @ReactMethod
  override fun me(promise: Promise) =
    onMain {
      UserApiClient.instance.me { user, error ->
        if (error != null) {
          promise.rejectWith(error)
        } else if (user == null) {
          promise.rejectWith(RNCKakaoResponseNotFoundException("user"))
        } else {
          promise.resolve(
            argMap().apply {
              putD("id", user.id?.toDouble())
              putString("name", user.kakaoAccount?.name)

              putString("email", user.kakaoAccount?.email)
              putString("nickname", user.kakaoAccount?.profile?.nickname)
              putString("profileImageUrl", user.kakaoAccount?.profile?.profileImageUrl)
              putString("thumbnailImageUrl", user.kakaoAccount?.profile?.thumbnailImageUrl)
              putString("phoneNumber", user.kakaoAccount?.phoneNumber)
              putString("ageRange", user.kakaoAccount?.ageRange?.name)
              putString("birthday", user.kakaoAccount?.birthday)
              putString("birthdayType", user.kakaoAccount?.birthdayType?.name)
              putString("birthyear", user.kakaoAccount?.birthyear)
              putString("gender", user.kakaoAccount?.gender?.name)
              putB("isEmailValid", user.kakaoAccount?.isEmailValid)
              putB("isEmailVerified", user.kakaoAccount?.isEmailVerified)
              putB("isKorean", user.kakaoAccount?.isKorean)
              putB(
                "ageRangeNeedsAgreement",
                user.kakaoAccount?.ageRangeNeedsAgreement,
              )
              putB(
                "birthdayNeedsAgreement",
                user.kakaoAccount?.birthdayNeedsAgreement,
              )
              putB(
                "birthyearNeedsAgreement",
                user.kakaoAccount?.birthyearNeedsAgreement,
              )
              putB(
                "emailNeedsAgreement",
                user.kakaoAccount?.emailNeedsAgreement,
              )
              putB(
                "genderNeedsAgreement",
                user.kakaoAccount?.genderNeedsAgreement,
              )
              putB(
                "isKoreanNeedsAgreement",
                user.kakaoAccount?.isKoreanNeedsAgreement,
              )
              putB(
                "phoneNumberNeedsAgreement",
                user.kakaoAccount?.phoneNumberNeedsAgreement,
              )
              putB(
                "profileNeedsAgreement",
                user.kakaoAccount?.profileNeedsAgreement,
              )
              putB(
                "ciNeedsAgreement",
                user.kakaoAccount?.ciNeedsAgreement,
              )
              putB(
                "nameNeedsAgreement",
                user.kakaoAccount?.nameNeedsAgreement,
              )
              putB(
                "profileImageNeedsAgreement",
                user.kakaoAccount?.profileImageNeedsAgreement,
              )
              putB(
                "profileNicknameNeedsAgreement",
                user.kakaoAccount?.profileNicknameNeedsAgreement,
              )
              putB(
                "legalBirthDateNeedsAgreement",
                user.kakaoAccount?.legalBirthDateNeedsAgreement,
              )
            },
          )
        }
      }
    }

  @ReactMethod
  override fun getAccessToken(promise: Promise) =
    onMain {
      UserApiClient.instance.accessTokenInfo { accessTokenInfo, error ->
        if (error != null) {
          promise.rejectWith(error)
        } else if (accessTokenInfo == null) {
          promise.rejectWith(RNCKakaoResponseNotFoundException("accessTokenInfo"))
        } else {
          promise.resolve(
            argMap().apply {
              putL("id", accessTokenInfo.id)
              putL("expiresIn", accessTokenInfo.expiresIn)
              putI("appID", accessTokenInfo.appId)
            },
          )
        }
      }
    }

  companion object {
    const val NAME = "RNCKakaoUser"
  }
}
