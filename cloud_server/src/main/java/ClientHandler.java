import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {
    private final NetworkServer networkServer;
    private final Socket clientSocket;
    private String loginFolder;
    private String login = "unlogin";

    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(NetworkServer networkServer, Socket clientSocket) {
        this.networkServer = networkServer;
        this.clientSocket = clientSocket;
        this.loginFolder = networkServer.getRootFolder();
    }

    public void run(){
        doHandle(clientSocket);
    }

    private void doHandle(Socket socket) {
        try {
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            ExecutorService executorService = Executors.newFixedThreadPool(1);
            executorService.execute(()->{
                try {
                    while (true){
                        String command = in.readUTF();
                        if(command.equals("/login")){

                            login = in.readUTF();
                            loginFolder = loginFolder + "/" +login;
                            FileUtility.createDirectory(loginFolder);

                        }else if (command.equals("/download")){

                            //имя файла для выгрузки
                            String fileName = in.readUTF();
                            File sendFile = FileUtility.createFile(loginFolder+"/"+fileName);

                            long fileSize = sendFile.length();
                            out.writeLong(fileSize);

                            //выгружаем файл в сокет
                            try(FileInputStream outFile = new FileInputStream(sendFile)){
                                byte[] bufferOut = new byte[8192];
                                long getCount = 0l;
                                while(fileSize > getCount){
                                    int count = outFile.read(bufferOut);
                                    if(count <= 0) break;
                                    getCount = getCount + count;
                                    out.write(bufferOut, 0, count);
                                }

                            }catch (IOException e){
                                System.out.println("Ошибка выгрузки файла");
                                e.printStackTrace();
                            }

                        }else if(command.equals("/upload")){

                            //имя файла для выгрузки
                            String fileName = in.readUTF();

                            long fileSize = in.readLong();

                            File loadFile = FileUtility.createFile(loginFolder+"/"+fileName);
                            try (FileOutputStream inFile = new FileOutputStream(loadFile)){
                                byte[] bufferIn = new byte[8192];
                                long getCount = 0l;
                                while(fileSize > getCount){
                                    int count = in.read(bufferIn);
                                    if(count <= 0) break;
                                    getCount = getCount + count;
                                    inFile.write(bufferIn, 0, count);
                                }

                            }catch (IOException e){
                                System.out.println("Ошибка загрузки файла");
                                e.printStackTrace();
                            }

                        }else if(command.equals("/all")){
                            File dir = FileUtility.createDirectory(loginFolder);
                            File[] files = dir.listFiles();
                            for (File file:files) {
                                out.writeUTF(file.getName());
                            }
                            out.writeUTF("/end_list");
                        }
                    }
                }catch (IOException e){
                    System.out.println("Соединение с клиентом " + login + " было закрыто!");
                } finally {
                    closeConnection();
                }
            });

            executorService.shutdown();

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void authLogin(){

    }

    private void closeConnection() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
