import java.io.IOException;

public class Server {
    private static final int DEFAULT_PORT = 8189;
    public static final String ROOT_FOLDER = "rootFolder";

    public static void main(String[] args) throws Exception {

        int port = DEFAULT_PORT;
        String rootFolder = ROOT_FOLDER;

        if(args.length > 0){
            try {
                port = Integer.parseInt(args[0]);
            }catch (Exception e){
                System.out.println("Некорректный формат порта, будет использоваться порт по умолчанию");
            }
        }

        if(args.length > 1){
            try {
                rootFolder = args[1];
            }catch (Exception e){
                System.out.println("Некорректный формат папки, будет использоваться папка по умолчанию");
            }
        }

        try {
            FileUtility.createDirectory("./" + rootFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }

        new NetworkSeverNetty(port, rootFolder).run();
    }
}
