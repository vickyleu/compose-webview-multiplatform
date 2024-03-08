import org.gradle.api.Project
import java.io.BufferedWriter
import java.io.File
import java.util.Properties
import java.util.regex.Pattern

fun Project.getBuildRelativeDir(): String {
    return this.layout.buildDirectory.get().asFile.let {
        val parent = this.projectDir.parentFile
        val relative = it.relativeTo(parent)
        return@let relative.path
    }
}


// 更新Xcode配置文件
fun File.updateXcodeConfigFile(vararg callable: Map<String, String>.() -> Pair<String, String>) {
    val xcconfigProperties = mutableMapOf<String, String>()
    val properties = Properties()
    var isChanged = false
    if (exists()) {
        reader(Charsets.UTF_8).use { reader ->
            properties.load(reader)
            properties.forEach { (key, value) ->
                if (key.toString().isNotBlank()) {
                    xcconfigProperties[key.toString()] = value.toString()
                }
            }
        }
    } else {
        createNewFile()
    }
    callable.map { it(xcconfigProperties) }.forEach {
        val (key, value) = it
        if (xcconfigProperties[key] != value) {
            xcconfigProperties[key] = value
            isChanged = true
        }
    }
    if (xcconfigProperties.keys.isNotEmpty() && isChanged) {
        bufferedWriter(Charsets.UTF_8).use { writer ->
            writeProperties(writer, xcconfigProperties, null)
        }
    }
}

// 写入属性文件
@Suppress("SameParameterValue")
private fun writeProperties(
    writer: BufferedWriter,
    properties: Map<String, String>,
    comments: String? = null
) {
    comments?.let {
        writer.write("# $it")
        writer.newLine()
    }
    properties.asSequence()
        .sortedBy { it.key }.toList()
        .onEachIndexed { index, entry ->
            writer.write("${entry.key}=${entry.value}")
            if (index < properties.size - 1) {
                writer.newLine()
            }
        }
    writer.flush()
}

private fun queryPlistFilesAndModuleName(dir: File): Pair<String, String>? {
    val listFiles = dir.listFiles()
    listFiles?.forEach {
        if (it.isDirectory) {
            if (it.name.endsWith(".xcodeproj")) {
                val pbxproj = it.resolve("project.pbxproj")
                if (pbxproj.exists()) {
                    return Pair(it.nameWithoutExtension, pbxproj.absolutePath)
                }
            } else {
                val pair = queryPlistFilesAndModuleName(it)
                if (pair != null) {
                    return pair
                }
            }
        }
    }
    return null
}

//需要检查所有的plist文件,UILaunchStoryboardName是否存在,如果不存在就要添加一个UILaunchStoryboardName的空key,如果是存在其他Launch设置就忽略
// 因为iOS设备没有设置启动屏幕的话,屏幕尺寸是错误的,所以需要检查一下,如果已经设置了就不需要再设置了
fun processPlistFiles(dir: File) {
    val pair = queryPlistFilesAndModuleName(dir) ?: return
    val moduleName = pair.first
    val pbxproj = File(pair.second)
    if (pbxproj.exists()) {
        val plists = getAllPlistFiles(pbxproj)
        plists.forEach {
            val plist = File(dir, "$moduleName/${it}")
            if (plist.exists()) {
                val originContent = plist.readText()
                var content = originContent
                // <key>UILaunchStoryboardName</key> 可能是<key>UILaunchStoryboardName*</key>的形式,所以需要使用正则表达式来匹配,
                // content直接contains的话有可能其他value里写了key,所以要移除掉
                // 空格,然后再匹配,避免有错误的匹配
                if (content.replace(" ", "").contains("<key>UILaunchStoryboardName</key>")) {
                    return
                }
                // UILaunchScreen,UILaunchImageName,UILaunchImages 都需要判断所对应的内容是否是空的,不能只判断key是否存在,因为key存在,但是value
                // 是空的话屏幕依然不会生效的,只有UILaunchStoryboardName空的可以自动计算出屏幕尺寸
                if (content.replace(" ", "").contains("<key>UILaunchScreen</key>")) {
                    // 正则判断内容是否是空的
                    val pattern =
                        Pattern.compile("<key>UILaunchScreen</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            return
                        }
                    }
                    // 如果走到这里,说明放了个空的key,那么就需要删除掉,删除的时候还是要用正则表达式,因为可能有空格
                    val patternDelete =
                        Pattern.compile("<key>UILaunchScreen</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }
                if (content.replace(" ", "").contains("<key>UILaunchImageName</key>")) {
                    val pattern =
                        Pattern.compile("<key>UILaunchImageName</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            return
                        }
                    }
                    val patternDelete =
                        Pattern.compile("<key>UILaunchImageName</key>[\\s\\S]*?<string>(.*?)</string>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }
                if (content.replace(" ", "").contains("<key>UILaunchImages</key>")) {
                    // UILaunchImages的内容是数组,所以需要判断是否是空的,不是空的话就不需要处理
                    val pattern =
                        Pattern.compile("<key>UILaunchImages</key>[\\s\\S]*?<array>(.*?)</array>")
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        val group = matcher.group(1)
                        if (group.isNotBlank()) {
                            println("UILaunchImages太复杂,无法判断,请手动处理")
                            return
                        }
                    }
                    val patternDelete =
                        Pattern.compile("<key>UILaunchImages</key>[\\s\\S]*?<array>(.*?)</array>")
                    val matcherDelete = patternDelete.matcher(content)
                    if (matcherDelete.find()) {
                        val group = matcherDelete.group()
                        val newContent = content.replace(group, "")
                        content = newContent
                    }
                }

                val patternSbn = Pattern.compile("<key>UILaunchStoryboardName[\\s\\S]*?</key>")
                val matcherSbn = patternSbn.matcher(content)
                if (matcherSbn.find()) {
                    return
                }
                val pattern = Pattern.compile("<plist version=\"1.0\">[\\s\\S]*?<dict>")
                val matcher = pattern.matcher(content)
                if (matcher.find()) {
                    val group = matcher.group()
                    val newContent = content.replace(
                        group,
                        "$group\n    <key>UILaunchStoryboardName</key>\n    <string></string>"
                    )
                    content = newContent
                }
                if (originContent != content) {
                    plist.writeText(content)
                }
            } else {
                throw RuntimeException("$moduleName/${it} file not found")
            }
        }
    }
}

private fun getAllPlistFiles(pbxproj: File): List<String> {
    val content = pbxproj.readText()
    val pattern =
        Pattern.compile("isa = XCBuildConfiguration;[\\s\\S]*?INFOPLIST_FILE = (.*?);[\\s\\S]*?name = (.*?);")
    val matcher = pattern.matcher(content)
    val result = mutableMapOf<String, String>()
    while (matcher.find()) {
        //plist需要取出前后的引号,必须是前后,不能是中间
        val plist = matcher.group(1).trim('\"').trim('\'')
        val name = matcher.group(2).trim('\"').trim('\'')
        if (!result.containsKey(name)) {
            val value = if (plist.contains("\$(CONFIGURATION)")) {
                plist.replace("\$(CONFIGURATION)", name)
            } else {
                plist
            }
            result[name] = value
        }
    }
    // 最后做个检查,如果value中有相同的值,就保留一个,没有则全部保留
    val map = mutableMapOf<String, String>()
    result.forEach { (key, value) ->
        if (!map.containsValue(value)) {
            map[key] = value
        }
    }
    return map.values.toList()
}