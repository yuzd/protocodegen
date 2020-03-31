import com.google.errorprone.annotations.Var;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class JavaExeBat {
    
    
    public static String Excute(String exe,String batPath,String logFile) {
        StringBuilder strBuilder = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder(exe);
            Process  process = builder.start();
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            InputStream stdout = process.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

            out.write("\""+batPath+"\"  >> \""+logFile+"\" 2>&1 \n");
            out.flush();

            out.write("echo %errorlevel% \n");
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
            if(re1.indexOf("echo %errorlevel% 1")>-1){
                return new String(Files.readAllBytes(Paths.get(logFile)));
            }
        }catch (Exception e){
            return e.getMessage();
        }
        return "";
    }
}
