package cn.bit101.android.net.school

import android.util.Log
import cn.bit101.android.database.DataStore
import cn.bit101.android.database.EncryptedStore
import cn.bit101.android.net.HttpClient
import cn.bit101.android.utils.AESUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * @author flwfdd
 * @date 2023/3/16 23:17
 * @description _(:з」∠)_
 */

suspend fun encryptPassword(password: String, salt: String): String? {
    if (salt.isEmpty()) {
        return password
    } else {
        return runCatching {
            withContext(Dispatchers.Main) {
                val data = AESUtils.randomString(64) + password
                AESUtils.encryptAES(data, salt, AESUtils.randomString(16))
            }
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

}

// 登陆
suspend fun login(username: String, password: String): Boolean {
    try {
        withContext(Dispatchers.IO) {
            val client = HttpClient.client

            // 登录初始化
            val initLoginRequest = Request.Builder()
                .url(schoolLoginUrl)
                .build()
            var cryptPassword = ""
            var execution = ""
            client.newCall(initLoginRequest).execute().use { response ->
                val html =
                    response.body?.string() ?: throw Exception("get login init response error")
                // Cookie已经被修改 修改登陆状态为未登录
                DataStore.setBoolean(DataStore.LOGIN_STATUS, false)
                val doc = Jsoup.parse(html)
                val form = doc.select("#pwdFromId")
                val salt = form.select("#pwdEncryptSalt").attr("value")
                execution = form.select("#execution").attr("value")
                cryptPassword =
                    encryptPassword(password, salt) ?: throw Exception("encrypt password error")
            }

            // 登录
            val body = FormBody.Builder()
                .add("username", username)
                .add("password", cryptPassword)
                .add("execution", execution)
                .add("captcha", "")
                .add("_eventId", "submit")
                .add("cllt", "userNameLogin")
                .add("dllt", "generalLogin")
                .add("lt", "")
                .add("rememberMe", "true")
                .build()
            val loginRequest = Request.Builder()
                .url(schoolLoginUrl)
                .post(body)
                .build()
            client.newCall(loginRequest).execute().use { response ->
                val html =
                    response.body?.string() ?: throw Exception("get login response error")
                if (html.indexOf("帐号登录或动态码登录") != -1) {
                    // 登陆失败 删除保存的学号密码
                    EncryptedStore.deleteString(EncryptedStore.SID)
                    EncryptedStore.deleteString(EncryptedStore.PASSWORD)
                    DataStore.setString(DataStore.LOGIN_SID, "")
                    throw Exception("login error")
                }
            }
            // 保存学号密码
            EncryptedStore.setString(EncryptedStore.SID, username)
            EncryptedStore.setString(EncryptedStore.PASSWORD, password)
            DataStore.setString(DataStore.LOGIN_SID, username)
            DataStore.setBoolean(DataStore.LOGIN_STATUS, true)
        }
    } catch (e: Exception) {
        Log.e("SchoolLogin", "Login Error $e")
        return false
    }
    return true
}

suspend fun checkLogin(): Boolean {
    try {
        withContext(Dispatchers.IO) {
            val client = HttpClient.client

            // 登录初始化
            val initLoginRequest = Request.Builder()
                .url(schoolLoginUrl)
                .build()
            client.newCall(initLoginRequest).execute().use { response ->
                val html =
                    response.body?.string() ?: throw Exception("check login response error")
                if (html.indexOf("帐号登录或动态码登录") != -1) {
                    // 设置登陆状态为未登录
                    DataStore.setBoolean(DataStore.LOGIN_STATUS, false)
                    throw Exception("username or password error")
                }
            }
            // 设置登陆状态为登陆
            DataStore.setBoolean(DataStore.LOGIN_STATUS, true)
        }
    } catch (e: Exception) {
        Log.e("SchoolLogin", "Login Error $e")
        return false
    }
    return true
}

// 退出登录
fun logout() {
    HttpClient.cookieManager.cookieStore.removeAll()
    EncryptedStore.deleteString(EncryptedStore.SID)
    EncryptedStore.deleteString(EncryptedStore.PASSWORD)
    DataStore.setString(DataStore.LOGIN_SID, "")
    DataStore.setBoolean(DataStore.LOGIN_STATUS, false)
}
