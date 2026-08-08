package com.example.immersivecompanion

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class AppSettings(
    val baseUrl: String = "https://api.apimart.ai",
    val apiKey: String = "",
    val chatModel: String = "",
    val imageModel: String = "",
    val imageSize: String = "1:1",
    val autoImage: Boolean = true,
    val maxTokens: Int = 0,
    val roleName: String = "澪",
    val rolePrompt: String = DEFAULT_ROLE_PROMPT,
    val imageStyle: String = "cinematic anime, soft lighting, immersive atmosphere",
)

data class ChatMessage(
    val id: Long,
    val role: String,
    val text: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
)

data class AnalysisResult(
    val shouldGenerateImage: Boolean,
    val imagePrompt: String,
    val memoryUpdate: String,
)

const val DEFAULT_ROLE_PROMPT = """你正在扮演一个可长期陪伴用户聊天的角色。
你不是普通问答助手，而是一个有稳定人设、关系感和情绪连续性的聊天对象。

要求：
1. 完全遵守用户在角色卡里设定的人设、关系、世界观和说话方式。
2. 回复要自然，像真实聊天，不要机械总结。
3. 不要主动说“作为AI”。
4. 不要强制缩短回复；根据上下文决定长短。
5. 记住上下文里的重要信息，并保持前后一致。
6. 如果用户只是闲聊，就自然接话；如果用户在写故事或角色扮演，就优先进入角色。"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompanionApp()
        }
    }
}

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("companion_chat_store", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            baseUrl = prefs.getString("baseUrl", "https://api.apimart.ai").orEmpty(),
            apiKey = prefs.getString("apiKey", "").orEmpty(),
            chatModel = prefs.getString("chatModel", "").orEmpty(),
            imageModel = prefs.getString("imageModel", "").orEmpty(),
            imageSize = prefs.getString("imageSize", "1:1").orEmpty(),
            autoImage = prefs.getBoolean("autoImage", true),
            maxTokens = prefs.getInt("maxTokens", 0),
            roleName = prefs.getString("roleName", "澪").orEmpty(),
            rolePrompt = prefs.getString("rolePrompt", DEFAULT_ROLE_PROMPT).orEmpty(),
            imageStyle = prefs.getString("imageStyle", "cinematic anime, soft lighting, immersive atmosphere").orEmpty(),
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("baseUrl", settings.baseUrl.trim())
            .putString("apiKey", settings.apiKey.trim())
            .putString("chatModel", settings.chatModel.trim())
            .putString("imageModel", settings.imageModel.trim())
            .putString("imageSize", settings.imageSize.trim())
            .putBoolean("autoImage", settings.autoImage)
            .putInt("maxTokens", settings.maxTokens)
            .putString("roleName", settings.roleName.trim())
            .putString("rolePrompt", settings.rolePrompt)
            .putString("imageStyle", settings.imageStyle)
            .apply()
    }

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString("messages", "[]").orEmpty()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    ChatMessage(
                        id = item.optLong("id"),
                        role = item.optString("role"),
                        text = item.optString("text"),
                        imageUrl = item.optString("imageUrl"),
                        imageBase64 = item.optString("imageBase64"),
                    )
                )
            }
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(120).forEach { msg ->
            arr.put(
                JSONObject()
                    .put("id", msg.id)
                    .put("role", msg.role)
                    .put("text", msg.text)
                    .put("imageUrl", msg.imageUrl)
                    .put("imageBase64", msg.imageBase64)
            )
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    fun loadMemory(): String = prefs.getString("memory", "").orEmpty()

    fun saveMemory(memory: String) {
        prefs.edit().putString("memory", memory.takeLast(6000)).apply()
    }

    fun clearMessages() {
        prefs.edit().putString("messages", "[]").apply()
    }

    fun clearMemory() {
        prefs.edit().putString("memory", "").apply()
    }
}

object OpenAiCompatApi {
    suspend fun fetchModels(settings: AppSettings): List<String> = withContext(Dispatchers.IO) {
        val text = request("GET", modelUrls(settings.baseUrl).first(), settings.apiKey, null)
        val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optString("id").orEmpty()
                if (id.isNotBlank()) add(id)
            }
        }.distinct().sorted()
    }

    suspend fun chatReply(settings: AppSettings, memory: String, messages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            require(settings.apiKey.isNotBlank()) { "请先填写 API Key。" }
            require(settings.chatModel.isNotBlank()) { "请先填写聊天模型名。" }

            val body = JSONObject()
                .put("model", settings.chatModel)
                .put("temperature", 0.9)

            if (settings.maxTokens > 0) body.put("max_tokens", settings.maxTokens)

            val apiMessages = JSONArray()
            apiMessages.put(JSONObject().put("role", "system").put("content", buildSystemPrompt(settings, memory)))
            messages.filter { it.role == "user" || it.role == "assistant" }
                .takeLast(30)
                .forEach { msg ->
                    apiMessages.put(
                        JSONObject()
                            .put("role", if (msg.role == "user") "user" else "assistant")
                            .put("content", msg.text)
                    )
                }
            body.put("messages", apiMessages)

            val text = request("POST", chatUrl(settings.baseUrl), settings.apiKey, body.toString())
            parseChatContent(text).trim()
        }

    suspend fun analyzeForImageAndMemory(
        settings: AppSettings,
        memory: String,
        recentMessages: List<ChatMessage>,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank() || settings.chatModel.isBlank()) return@withContext AnalysisResult(false, "", "")

        val visible = recentMessages
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(8)
            .joinToString("\n") { "${it.role}: ${it.text}" }

        val system = """
            You are an invisible app-side parser. Return only valid JSON.
            Decide whether the recent conversation would benefit from a generated image.
            Also extract durable memory facts explicitly stated by the user.

            JSON schema:
            {
              "should_generate_image": true,
              "image_prompt": "English image prompt, concrete visual details only",
              "memory_update": "Chinese memory note, empty if nothing important"
            }

            Rules:
            - Generate an image for concrete scenes, rooms, outfits, characters, fantasy settings, places, moods, or story moments.
            - Do not generate an image for ordinary settings changes or short factual questions.
            - The image prompt must be in English.
            - Do not invent memory. Only store stable facts or preferences from the user.
        """.trimIndent()

        val body = JSONObject()
            .put("model", settings.chatModel)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", "Current memory:\n$memory\n\nRecent conversation:\n$visible"))
            )

        val text = request("POST", chatUrl(settings.baseUrl), settings.apiKey, body.toString())
        val content = parseChatContent(text)
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonText = content.extractJsonObject()
        val json = JSONObject(jsonText)
        AnalysisResult(
            shouldGenerateImage = json.optBoolean("should_generate_image", false),
            imagePrompt = json.optString("image_prompt").trim(),
            memoryUpdate = json.optString("memory_update").trim(),
        )
    }

    suspend fun generateImage(settings: AppSettings, prompt: String): ChatMessage = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) { "请先填写 API Key。" }
        require(settings.imageModel.isNotBlank()) { "请先填写图片模型名。" }
        require(prompt.isNotBlank()) { "没有可用的图片提示词。" }

        val fullPrompt = buildString {
            append(prompt)
            if (settings.imageStyle.isNotBlank()) append(", style: ${settings.imageStyle}")
        }

        val body = JSONObject()
            .put("model", settings.imageModel)
            .put("prompt", fullPrompt)
            .put("n", 1)
            .put("size", settings.imageSize.ifBlank { "1:1" })

        if (isApimart(settings.baseUrl)) {
            body.put("resolution", "1K")
        }

        val text = request("POST", imageUrl(settings.baseUrl), settings.apiKey, body.toString())
        val image = parseImageResponseOrTask(settings, text)
        return@withContext ChatMessage(
            id = System.currentTimeMillis(),
            role = "image",
            text = "Image prompt: $fullPrompt",
            imageUrl = image.first,
            imageBase64 = image.second,
        )

        val first = JSONObject(text).getJSONArray("data").get(0)
        val imageUrl: String
        val imageBase64: String
        if (first is JSONObject) {
            imageUrl = first.optString("url")
            imageBase64 = first.optString("b64_json")
        } else {
            imageUrl = first.toString()
            imageBase64 = ""
        }
        ChatMessage(
            id = System.currentTimeMillis(),
            role = "image",
            text = "图片提示词：$fullPrompt",
            imageUrl = imageUrl,
            imageBase64 = imageBase64,
        )
    }

    private suspend fun parseImageResponseOrTask(settings: AppSettings, responseText: String): Pair<String, String> {
        val direct = parseImageUrl(responseText)
        if (direct.first.isNotBlank() || direct.second.isNotBlank()) return direct

        val taskId = parseTaskId(responseText)
        if (taskId.isBlank()) throw IllegalStateException("Unrecognized image response: ${responseText.take(500)}")

        repeat(45) {
            delay(2_000)
            val taskText = request("GET", taskUrl(settings.baseUrl, taskId), settings.apiKey, null)
            val status = parseTaskStatus(taskText)
            if (status.equals("failed", ignoreCase = true)) {
                throw IllegalStateException("Image task failed: ${taskText.take(500)}")
            }
            val taskImage = parseImageUrl(taskText)
            if (taskImage.first.isNotBlank() || taskImage.second.isNotBlank()) return taskImage
        }

        throw IllegalStateException("Image task timeout: $taskId")
    }

    private fun parseImageUrl(responseText: String): Pair<String, String> {
        val root = JSONObject(responseText)
        root.optJSONObject("error")?.let { throw IllegalStateException(it.optString("message", it.toString())) }

        val data = root.opt("data")
        if (data is JSONArray && data.length() > 0) {
            val first = data.get(0)
            if (first is JSONObject) {
                val url = first.optString("url")
                val b64 = first.optString("b64_json")
                if (url.isNotBlank() || b64.isNotBlank()) return url to b64
            } else if (first.toString().startsWith("http")) {
                return first.toString() to ""
            }
        }

        val dataObj = data as? JSONObject ?: root
        val result = dataObj.optJSONObject("result")
        val images = result?.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val imageObj = images.optJSONObject(0)
            val urlValue = imageObj?.opt("url")
            when (urlValue) {
                is JSONArray -> if (urlValue.length() > 0) return urlValue.optString(0) to ""
                is String -> if (urlValue.isNotBlank()) return urlValue to ""
            }
        }

        val directUrl = dataObj.optString("url")
        if (directUrl.isNotBlank()) return directUrl to ""
        val directB64 = dataObj.optString("b64_json")
        if (directB64.isNotBlank()) return "" to directB64

        return "" to ""
    }

    private fun parseTaskId(responseText: String): String {
        val root = JSONObject(responseText)
        val data = root.opt("data")
        if (data is JSONArray && data.length() > 0) {
            val first = data.optJSONObject(0)
            if (first != null) return first.optString("task_id").ifBlank { first.optString("id") }
        }
        if (data is JSONObject) return data.optString("task_id").ifBlank { data.optString("id") }
        return root.optString("task_id").ifBlank { root.optString("id") }
    }

    private fun parseTaskStatus(responseText: String): String {
        val root = JSONObject(responseText)
        val data = root.opt("data")
        if (data is JSONObject) return data.optString("status")
        return root.optString("status")
    }

    private fun parseChatContent(responseText: String): String {
        val root = JSONObject(responseText)
        root.optJSONObject("error")?.let { throw IllegalStateException(it.optString("message", it.toString())) }

        val wrappedData = root.opt("data")
        if (wrappedData is JSONObject) {
            wrappedData.optJSONObject("error")?.let { throw IllegalStateException(it.optString("message", it.toString())) }
            val wrappedChoices = wrappedData.optJSONArray("choices")
            if (wrappedChoices != null && wrappedChoices.length() > 0) {
                val choice = wrappedChoices.get(0)
                if (choice is JSONObject) {
                    val message = choice.opt("message")
                    when (message) {
                        is JSONObject -> return contentToText(message.opt("content"))
                        is String -> return message
                    }
                    val text = choice.optString("text")
                    if (text.isNotBlank()) return text
                }
            }
        }

        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.get(0)
            if (choice is JSONObject) {
                val message = choice.opt("message")
                when (message) {
                    is JSONObject -> return contentToText(message.opt("content"))
                    is String -> return message
                }
                val delta = choice.opt("delta")
                if (delta is JSONObject) return contentToText(delta.opt("content"))
                val text = choice.optString("text")
                if (text.isNotBlank()) return text
            } else {
                return choice.toString()
            }
        }

        val message = root.opt("message")
        when (message) {
            is JSONObject -> return contentToText(message.opt("content"))
            is String -> if (message.isNotBlank()) return message
        }

        val data = root.opt("data")
        when (data) {
            is JSONObject -> {
                val content = contentToText(data.opt("content"))
                if (content.isNotBlank()) return content
                val output = contentToText(data.opt("output"))
                if (output.isNotBlank()) return output
            }
            is String -> if (data.isNotBlank()) return data
        }

        val output = contentToText(root.opt("output"))
        if (output.isNotBlank()) return output

        val content = contentToText(root.opt("content"))
        if (content.isNotBlank()) return content

        throw IllegalStateException("接口返回格式无法识别：${responseText.take(500)}")
    }

    private fun contentToText(value: Any?): String {
        return when (value) {
            null -> ""
            JSONObject.NULL -> ""
            is String -> value
            is JSONArray -> buildString {
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    when (item) {
                        is JSONObject -> {
                            val text = item.optString("text")
                            val content = item.optString("content")
                            val imageUrl = item.optString("image_url")
                            when {
                                text.isNotBlank() -> append(text)
                                content.isNotBlank() -> append(content)
                                imageUrl.isNotBlank() -> append(imageUrl)
                            }
                        }
                        else -> append(item.toString())
                    }
                    if (i != value.length() - 1) append('\n')
                }
            }
            is JSONObject -> {
                val text = value.optString("text")
                if (text.isNotBlank()) text else value.toString()
            }
            else -> value.toString()
        }
    }

    private fun buildSystemPrompt(settings: AppSettings, memory: String): String {
        return """
            角色名称：${settings.roleName}

            角色设定：
            ${settings.rolePrompt}

            长期记忆：
            ${memory.ifBlank { "暂无。" }}

            当前任务：
            保持角色扮演和上下文连续性，直接回复用户。不要输出 JSON，不要解释后台规则。
        """.trimIndent()
    }

    private fun chatUrl(baseUrl: String): String {
        return if (isApimart(baseUrl)) {
            "https://api.apimart.ai/api/v1/chat/completions"
        } else {
            joinUrl(baseUrl, "chat/completions")
        }
    }

    private fun imageUrl(baseUrl: String): String {
        return if (isApimart(baseUrl)) {
            "https://api.apimart.ai/v1/images/generations"
        } else {
            joinUrl(baseUrl, "images/generations")
        }
    }

    private fun taskUrl(baseUrl: String, taskId: String): String {
        return if (isApimart(baseUrl)) {
            "https://api.apimart.ai/v1/tasks/$taskId"
        } else {
            joinUrl(baseUrl, "tasks/$taskId")
        }
    }

    private fun modelUrls(baseUrl: String): List<String> {
        return if (isApimart(baseUrl)) {
            listOf("https://api.apimart.ai/api/v1/models")
        } else {
            listOf(joinUrl(baseUrl, "models"))
        }
    }

    private fun isApimart(baseUrl: String): Boolean {
        return baseUrl.contains("apimart.ai", ignoreCase = true)
    }

    private fun joinUrl(baseUrl: String, path: String): String {
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun request(method: String, url: String, apiKey: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 60_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}: $text")
        }
        return text
    }
}

private fun String.extractJsonObject(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end > start) substring(start, end + 1) else this
}

@Composable
fun CompanionApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var settings by remember { mutableStateOf(store.loadSettings()) }
    var memory by remember { mutableStateOf(store.loadMemory()) }
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(store.loadMessages()) } }
    var currentScreen by remember { mutableStateOf("chat") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun persistMessages() = store.saveMessages(messages)
    fun appendMemory(update: String) {
        if (update.isBlank()) return
        memory = listOf(memory, "- $update").filter { it.isNotBlank() }.joinToString("\n").takeLast(6000)
        store.saveMemory(memory)
    }

    fun runImageGeneration() {
        scope.launch {
            busy = true
            status = "正在分析图片提示词..."
            try {
                val analysis = OpenAiCompatApi.analyzeForImageAndMemory(settings, memory, messages)
                appendMemory(analysis.memoryUpdate)
                if (analysis.imagePrompt.isBlank()) {
                    status = "当前内容没有提取到明确画面。"
                } else {
                    status = "正在生成图片..."
                    val image = OpenAiCompatApi.generateImage(settings, analysis.imagePrompt)
                    messages.add(image)
                    persistMessages()
                    status = ""
                }
            } catch (e: Exception) {
                status = e.message ?: "图片生成失败。"
            } finally {
                busy = false
            }
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank() || busy) return
        input = ""
        messages.add(ChatMessage(System.currentTimeMillis(), "user", text))
        persistMessages()
        scope.launch {
            busy = true
            status = "正在回复..."
            try {
                val reply = OpenAiCompatApi.chatReply(settings, memory, messages)
                messages.add(ChatMessage(System.currentTimeMillis() + 1, "assistant", reply))
                persistMessages()
                status = "正在更新记忆..."
                val analysis = OpenAiCompatApi.analyzeForImageAndMemory(settings, memory, messages)
                appendMemory(analysis.memoryUpdate)
                if (settings.autoImage && analysis.shouldGenerateImage && analysis.imagePrompt.isNotBlank()) {
                    status = "正在生成图片..."
                    val image = OpenAiCompatApi.generateImage(settings, analysis.imagePrompt)
                    messages.add(image)
                    persistMessages()
                }
                status = ""
            } catch (e: Exception) {
                messages.add(ChatMessage(System.currentTimeMillis() + 2, "assistant", "请求失败：${e.message}"))
                persistMessages()
                status = ""
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF090A0F),
            surface = Color(0xFF11131C),
            primary = Color(0xFFB9C7FF),
            secondary = Color(0xFFE8B4A7),
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    AppTopBar(
                        screen = currentScreen,
                        onScreen = { currentScreen = it },
                        roleName = settings.roleName,
                    )
                },
                bottomBar = {
                    if (currentScreen == "chat") {
                        ChatInputBar(
                            input = input,
                            onInput = { input = it },
                            onSend = { sendMessage() },
                            onImage = { runImageGeneration() },
                            busy = busy,
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (currentScreen) {
                        "settings" -> SettingsScreen(
                            settings = settings,
                            onSettings = {
                                settings = it
                                store.saveSettings(it)
                            },
                        )
                        "memory" -> MemoryScreen(
                            memory = memory,
                            onMemory = {
                                memory = it
                                store.saveMemory(it)
                            },
                            onClearMemory = {
                                memory = ""
                                store.clearMemory()
                            },
                            onClearMessages = {
                                messages.clear()
                                store.clearMessages()
                            },
                        )
                        else -> ChatScreen(messages = messages, busy = busy, status = status, roleName = settings.roleName)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(screen: String, onScreen: (String) -> Unit, roleName: String) {
    TopAppBar(
        title = {
            Column {
                Text(roleName.ifBlank { "沉浸陪伴聊天" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("v1.2 · APIMart路径修复 · 本地记忆", fontSize = 11.sp, color = Color(0xFF9AA1B8))
            }
        },
        actions = {
            TextButton(onClick = { onScreen("chat") }) {
                Text("聊天", color = if (screen == "chat") Color.White else Color(0xFF9AA1B8))
            }
            TextButton(onClick = { onScreen("memory") }) {
                Text("记忆", color = if (screen == "memory") Color.White else Color(0xFF9AA1B8))
            }
            TextButton(onClick = { onScreen("settings") }) {
                Text("设置", color = if (screen == "settings") Color.White else Color(0xFF9AA1B8))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090A0F)),
    )
}

@Composable
fun ChatScreen(messages: List<ChatMessage>, busy: Boolean, status: String, roleName: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (messages.isEmpty()) {
            item {
                EmptyState(roleName)
            }
        }
        items(messages, key = { it.id }) { msg ->
            MessageBubble(msg, roleName)
        }
        if (busy || status.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(status, color = Color(0xFF9AA1B8), fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
fun EmptyState(roleName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11131C)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("开始和 ${roleName.ifBlank { "角色" }} 聊天", fontWeight = FontWeight.Bold, color = Color.White)
            Text("先到设置页填写 API Key、聊天模型和图片模型。角色设定可以直接编辑。", color = Color(0xFFBAC0D5), lineHeight = 20.sp)
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, roleName: String) {
    val isUser = message.role == "user"
    val isImage = message.role == "image"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) Avatar(label = if (isImage) "图" else roleName.take(1).ifBlank { "AI" })
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.86f else 0.9f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isUser) Color(0xFF2F477A) else Color(0xFF171A25))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.text.isNotBlank()) {
                Text(message.text, color = Color.White, lineHeight = 21.sp)
            }
            if (isImage) {
                GeneratedImage(message)
            }
        }
        if (isUser) Avatar(label = "我")
    }
}

@Composable
fun Avatar(label: String) {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF27304A)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(2), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GeneratedImage(message: ChatMessage) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, message.imageUrl, message.imageBase64) {
        value = withContext(Dispatchers.IO) {
            when {
                message.imageBase64.isNotBlank() -> {
                    val bytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                message.imageUrl.isNotBlank() -> {
                    URL(message.imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }
                else -> null
            }
        }
    }

    val bitmap = bitmapState.value
    if (bitmap == null) {
        Text("图片加载中或接口未返回图片地址。", color = Color(0xFFBAC0D5), fontSize = 13.sp)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "generated image",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun ChatInputBar(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onImage: () -> Unit,
    busy: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A0F))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入聊天内容...") },
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onSend,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5568B8)),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("发送") }
                TextButton(onClick = onImage, enabled = !busy) { Text("生成图") }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("chat") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("API 设置") }
        item {
            SettingsTextField("Base URL", settings.baseUrl) { onSettings(settings.copy(baseUrl = it)) }
        }
        item {
            SettingsTextField(
                label = "API Key",
                value = settings.apiKey,
                password = true,
                onValue = { onSettings(settings.copy(apiKey = it)) },
            )
        }
        item {
            SettingsTextField("聊天模型", settings.chatModel) { onSettings(settings.copy(chatModel = it)) }
        }
        item {
            SettingsTextField("图片模型", settings.imageModel) { onSettings(settings.copy(imageModel = it)) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = settings.autoImage, onCheckedChange = { onSettings(settings.copy(autoImage = it)) })
                Text("自动根据聊天内容生成图片", color = Color.White)
            }
        }
        item {
            SettingsTextField("图片尺寸", settings.imageSize) { onSettings(settings.copy(imageSize = it)) }
        }
        item {
            SettingsTextField("最大回复 tokens，0 表示不主动限制", settings.maxTokens.toString()) {
                onSettings(settings.copy(maxTokens = it.toIntOrNull() ?: 0))
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        loadingModels = true
                        modelStatus = "正在获取模型列表..."
                        try {
                            models = OpenAiCompatApi.fetchModels(settings)
                            modelStatus = if (models.isEmpty()) "没有读取到模型。" else "读取到 ${models.size} 个模型。"
                        } catch (e: Exception) {
                            modelStatus = e.message ?: "模型列表读取失败。"
                        } finally {
                            loadingModels = false
                        }
                    }
                },
                enabled = !loadingModels,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (loadingModels) "读取中..." else "从 /models 获取模型列表")
            }
            if (modelStatus.isNotBlank()) Text(modelStatus, color = Color(0xFFBAC0D5), fontSize = 13.sp)
        }
        if (models.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = target == "chat", onClick = { target = "chat" }, label = { Text("设为聊天模型") })
                    FilterChip(selected = target == "image", onClick = { target = "image" }, label = { Text("设为图片模型") })
                }
            }
            items(models.take(80)) { model ->
                TextButton(
                    onClick = {
                        onSettings(if (target == "chat") settings.copy(chatModel = model) else settings.copy(imageModel = model))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(model, color = Color(0xFFDCE3FF))
                }
            }
        }
        item { Divider(color = Color(0xFF2A2E3F)) }
        item { SectionTitle("角色卡") }
        item {
            SettingsTextField("角色名称", settings.roleName) { onSettings(settings.copy(roleName = it)) }
        }
        item {
            SettingsTextField("角色设定", settings.rolePrompt, minLines = 8, maxLines = 14) {
                onSettings(settings.copy(rolePrompt = it))
            }
        }
        item {
            SettingsTextField("图片风格追加词", settings.imageStyle, minLines = 2, maxLines = 4) {
                onSettings(settings.copy(imageStyle = it))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun MemoryScreen(
    memory: String,
    onMemory: (String) -> Unit,
    onClearMemory: () -> Unit,
    onClearMessages: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("长期记忆") }
        item {
            SettingsTextField(
                label = "可手动编辑",
                value = memory,
                minLines = 12,
                maxLines = 18,
                onValue = onMemory,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onClearMemory, shape = RoundedCornerShape(8.dp)) { Text("清空记忆") }
                Button(onClick = onClearMessages, shape = RoundedCornerShape(8.dp)) { Text("清空聊天") }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    password: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}
