import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import org.apache.commons.io.input.BOMInputStream
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread


class ProtoCodeGen : AnAction() {
    private val NOTIFICATION_GROUP = NotificationGroup("ProtoCodeGen", NotificationDisplayType.BALLOON, true)
    private val log: Logger = Logger.getInstance("ProtoCodeGen")

    @Volatile
    private var ISRUN = false

    override fun actionPerformed(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val folderPath = virtualFile.path
        val propertiesFile = Paths.get(folderPath, "proto.properties");
        if (Files.notExists(propertiesFile)) {
            Messages.showMessageDialog(
                "can not find `proto.properties` file in $folderPath",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }
        val config = Properties()
        config.load(
            InputStreamReader(
                BOMInputStream(File(propertiesFile.toString()).inputStream()),
                StandardCharsets.UTF_8
            )
        )
        var outPut = config.getProperty("outFolder", null)
        if (outPut.isNullOrEmpty()) {
            Messages.showMessageDialog(
                "outFolder is required in `proto.properties` file",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }
        
        var lang = config.getProperty("lang","java")
        
        val basePath = FileSystems.getDefault().getPath(folderPath)
        val resolvedPath = basePath.parent.resolve(outPut)
        outPut = resolvedPath.normalize().toString()
        if(!Files.exists(Paths.get(outPut))){
            Messages.showMessageDialog(
                    "$outPut not exist",
                    "Error",
                    Messages.getErrorIcon()
            );
            return;
        }
        
        val protoFiles = File(folderPath).listFiles()?.filter { it.extension == "proto" }
        if (protoFiles == null || !protoFiles.any()) {
            Messages.showMessageDialog(
                "can not find any proto file in $folderPath",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val genFolderURL = ProtoCodeGen::class.java.classLoader.getResource("/gen/version.txt")
        if (genFolderURL == null) {
            Messages.showMessageDialog(
                "can not read version file in Resource",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val version = genFolderURL.readText()
        log.info("code gen plugin version:$version")
        val ostype = OsCheck.getOperatingSystemType()
        val execFile = if (ostype == OsCheck.OSType.MacOS) "protoc$version" else "protoc$version.exe"

        //找到当前plugin所在的地方
        val pluginPath = PluginManager.getPlugin(PluginId.getId("org.yuzd.codegen.protocodegen"))?.path?.absolutePath
        if (pluginPath.isNullOrEmpty()) {
            Messages.showMessageDialog(
                "can not get plugin `orm code gen` path",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val exePath = Paths.get(pluginPath, execFile)
        if (!Files.exists(exePath)) {
            try {
                ProtoCodeGen::class.java.classLoader.getResourceAsStream("/gen/$execFile")
                    .use { stream ->
                        Files.copy(stream, exePath)
                    }
            } catch (e: IOException) {
                Messages.showMessageDialog(
                    e.message,
                    "Error",
                    Messages.getErrorIcon()
                );
                return;
            }
        }

        if (!Files.exists(exePath)) {
            Messages.showMessageDialog(
                "load codeGen agent in plugin `orm code gen` fail",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        val project = e.dataContext.getData(PlatformDataKeys.PROJECT)
        if (project == null) {
            Messages.showMessageDialog(
                "get current project fail",
                "Error",
                Messages.getErrorIcon()
            );
            return;
        }

        thread {
            ISRUN = true;
            val resultRT = doAction(folderPath,pluginPath, exePath, execFile, outPut, protoFiles, ostype,lang)
            ISRUN = false;
            val result = resultRT.second
            ApplicationManager.getApplication().invokeLater {
                when {
                    result != 0 -> {
                        val notice = NOTIFICATION_GROUP.createNotification(
                            resultRT.first,
                            NotificationType.ERROR
                        )
                        notice.notify(project)
                    }
                    else -> {
                        val notice =
                            NOTIFICATION_GROUP.createNotification("proto codegen success", NotificationType.INFORMATION)
                        notice.notify(project)
                    }
                }
            }
        }

    }

    private fun doAction(
        folderPath:String,
        pluginPath: String,
        exePath: Path,
        execFile: String,
        outPut: String,
        protoFiles: List<File>,
        osType: OsCheck.OSType,
        lang:String
    ): Pair<String, Int> {
        var result = 0;
        var msg = "";
        try {
            val tempScriptLog: File = File.createTempFile("genscript", ".txt")
            val bashFile = if (osType == OsCheck.OSType.MacOS) createTempScript(
                folderPath,
                pluginPath, 
                execFile,
                protoFiles,
                outPut,lang
            ) else createWindowsTempScript(folderPath,pluginPath, exePath.toString(), protoFiles, outPut,lang)
            try {
                msg = JavaExeBat.Excute(osType,bashFile.toString(),tempScriptLog.absolutePath)
                if (msg.isNotEmpty()) {
                    result = 1;
                }
            } finally {
                bashFile?.delete()
                tempScriptLog.delete()
            }
        } catch (e: Exception) {
            msg = e.message ?: "err";
        }
        return Pair(msg, result);
    }


    private fun InputStream.readAll(): String {
        return try {
            val sc = Scanner(this)
            val sb = StringBuffer()
            while (sc.hasNext()) {
                sb.append(sc.nextLine())
            }
            sb.toString()
        } catch (e: Exception) {
            e.message ?: "uncatch err";
        }
    }

    @Throws(IOException::class)
    fun createTempScript(folderPath:String,folder: String, fileName: String, files: List<File>, outPut: String,lang:String): File? {
        val tempScript: File = File.createTempFile("genscript", ".sh")
        val streamWriter: Writer = OutputStreamWriter(
            FileOutputStream(
                tempScript
            )
        )
        val printWriter = PrintWriter(streamWriter)
        printWriter.println("#!/bin/bash")
        printWriter.println("set -e")
        // folder 是plugin 所在的文件夹
        printWriter.println("chmod -R 777 \"$folder\"")
        printWriter.println("cd \"$folder\"")
        
        files.forEach {
            printWriter.println("./$fileName -I protobuf --${lang}_out=\"$outPut\" \"${it.absolutePath}\" --proto_path=\"$folderPath\"")
        }
        printWriter.close()
        return tempScript
    }

    @Throws(IOException::class)
    fun createWindowsTempScript(folderPath:String,folder: String, fileName: String, files: List<File>, outPut: String,lang:String): File? {
        val tempScript: File = File.createTempFile("genscript", ".bat")
        val streamWriter: Writer = OutputStreamWriter(
            FileOutputStream(
                tempScript
            )
        )
        val printWriter = PrintWriter(streamWriter)
        printWriter.println("@ECHO OFF")
        printWriter.println("cd /d \"$folderPath\"") // cd 到 选择的文件夹
        files.forEach {
            //fileName 是 protoc的完整路径
            // outPut是填写的相对路径
            printWriter.println("\"$fileName\" -I protobuf --${lang}_out=\"$outPut\" ${it.name} --proto_path=\"$folderPath\"")
        }
        printWriter.close()
        return tempScript
    }
    
    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible =
            !ISRUN && virtualFile != null && virtualFile.isDirectory && virtualFile.name.contains("proto")
    }
}