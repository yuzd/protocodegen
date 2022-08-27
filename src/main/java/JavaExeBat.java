import com.google.errorprone.annotations.Var;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class JavaExeBat {


    /**
     * use bat to call proto.exe
     * @param osType
     * @param batPath
     * @param logFile
     * @return
     */
    public static String Excute(OsCheck.OSType osType, String batPath, String logFile) {
        StringBuilder strBuilder = new StringBuilder();
        try {
            
            if(osType != OsCheck.OSType.MacOS){
                String exe = "cmd.exe";
                ProcessBuilder builder = new ProcessBuilder(exe);
                Process  process = builder.start();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                InputStream stdout = process.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

                out.write("\""+batPath+"\"  >> \""+logFile+"\" 2>&1 \n");
                out.flush();

                out.write("echo %errorlevel% \n");//:"echo $? \n"
                out.flush();
                out.close();

                int re = process.waitFor();
                if(re != 0){
                    return new String(Files.readAllBytes(Paths.get(logFile)));
                }

                Scanner scanner = new Scanner(stdout);
                while (scanner.hasNextLine()) {
                    strBuilder.append(scanner.nextLine());
                }
                scanner.close();
                String re1 = strBuilder.toString();
                if(re1.contains("echo %errorlevel% 1")){
                    String res = new String(Files.readAllBytes(Paths.get(logFile)));
                    res = res.replace("protobuf: warning: directory does not exist.","");
                    return res;
                }
            }else{
                ProcessBuilder builder = new ProcessBuilder( "/bin/bash" );
                builder.directory(new File(batPath).getParentFile());
                Process p=builder.start();
                BufferedWriter p_stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
               
                p_stdin.write("chmod -R 777 " +new File(batPath).getName() );
                p_stdin.newLine();
                p_stdin.flush();
                
                p_stdin.write("./"+new File(batPath).getName() + " 2>" + new File(logFile).getName());
                p_stdin.newLine();
                p_stdin.flush();
                
                p_stdin.write("echo $?");
                p_stdin.newLine();
                p_stdin.flush();
                p_stdin.close();
                
                Scanner s = new Scanner( p.getInputStream() );
                while (s.hasNext())
                {
                    strBuilder.append(s.nextLine());
                }
                
                if(strBuilder.toString().equals("1")){
                    String res = new String(Files.readAllBytes(Paths.get(logFile)));
                    res = res.replace("protobuf: warning: directory does not exist.","");
                    return res;
                }
            }
        }catch (Exception e){
            return e.getMessage();
        }
        return "";
    }
}
