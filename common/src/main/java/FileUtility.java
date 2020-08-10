import java.io.File;
import java.io.IOException;

public class FileUtility {
    public static File createFile(String filename) throws IOException {
        File file = new File(filename);
        if(!file.exists()){
            file.createNewFile();
        }
        return file;
    }

    public static File createDirectory(String dirname) throws IOException{
        File file = new File(dirname);
        if(!file.exists()){
            file.mkdir();
        }
        return file;
    }
}
